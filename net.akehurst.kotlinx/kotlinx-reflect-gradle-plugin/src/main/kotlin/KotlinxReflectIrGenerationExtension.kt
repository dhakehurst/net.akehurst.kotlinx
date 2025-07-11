package net.akehurst.kotlinx.reflect.gradle.plugin

import net.akehurst.kotlinx.reflect.KotlinxReflect
import net.akehurst.kotlinx.text.toRegexFromGlob
import org.jetbrains.kotlin.analyzer.moduleInfo
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContextImpl
import org.jetbrains.kotlin.backend.common.ir.*
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.serialization.KotlinIrLinker
import org.jetbrains.kotlin.builtins.createFunctionType
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.EmptyPackageFragmentDescriptor
import org.jetbrains.kotlin.ir.*
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.safeName
import org.jetbrains.kotlin.ir.backend.js.utils.isJsExport
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrRawFunctionReferenceImpl
import org.jetbrains.kotlin.ir.overrides.isEffectivelyPrivate
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.JsPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

class KotlinxReflectIrGenerationExtension(
    private val messageCollector: MessageCollector,
    private val kotlinxReflectRegisterForModuleClassFqName: String,
    private val forReflection: List<String>
) : IrGenerationExtension {

    companion object {
        val fq_KotlinxReflect = ClassId.fromString(KotlinxReflect::class.qualifiedName!!.replace(".", "/"))
        val fq_registerClass = CallableId(fq_KotlinxReflect, Name.identifier("registerClass"))
        val fq_classForName = CallableId(fq_KotlinxReflect, Name.identifier("classForName"))
        const val classForNameAfterRegistration = "classForNameAfterRegistration"
        const val registerUsedClasses = "registerUsedClasses"

        // fun IrBuilderWithScope.irFunctionReference(type: IrType, symbol: IrFunctionSymbol, typeArgumentsCount: Int = 0, valueArgumentsCount: Int = 0) =
        //    IrFunctionReferenceImpl(startOffset, endOffset, type, symbol, typeArgumentsCount, valueArgumentsCount)
    }

    private val globRegexes = forReflection.mapNotNull {
        if (it.isBlank()) {
            messageCollector.report(CompilerMessageSeverity.WARNING, "InternalError: Got and ignored null or blank class name!")
            null
        } else {
            Pair(it.toRegexFromGlob('.'), it)
        }
    }.associate {
        Pair(it.first, Pair(it.second, false))
    }.toMutableMap()

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val isJvm = pluginContext.platform?.any { it is JvmPlatform } ?: false
        val isJs = pluginContext.platform?.any { it is JsPlatform } ?: false

        messageCollector.report(CompilerMessageSeverity.INFO, "KotlinxReflect: for moduleFragment - '${moduleFragment.name}'")
        messageCollector.report(CompilerMessageSeverity.LOGGING, "KotlinxReflect: forReflection = $forReflection")

        // Classes for Reflection from other modules (using member scope and descriptor)
        val classesToRegisterForReflection = mutableSetOf<IrClassSymbol>()
        forReflection.forEach { pkgName ->
            when {
                pkgName.contains("*").not() -> {
                    val pkgFqName = FqName(pkgName)
                    messageCollector.report(CompilerMessageSeverity.LOGGING, "KotlinxReflect: Checking content of package (1) '$pkgName'")

                    // This works in JS but not JVM ?
                    val pkgContent = moduleFragment.descriptor.allDependencyModules.flatMap { dep ->
                        messageCollector.report(CompilerMessageSeverity.INFO, "KotlinxReflect: Checking dependency '${dep.name}'")
                        val pkg = dep.getPackage(pkgFqName)
                        pkg.memberScope.getClassifierNames()?.map { cls -> Pair(cls, pluginContext.referenceClass(ClassId(pkgFqName, cls))) } ?: emptyList()
                    }
                    if (pkgContent.isEmpty()) {
                        messageCollector.report(CompilerMessageSeverity.WARNING, "KotlinxReflect: No content found for package '$pkgName'")
                    }
                    pkgContent.forEach { p ->
                        val cls = p.first
                        val sym = p.second
                        if (null != sym) {// && true==sym.signature?.isPubliclyVisible) {
                            messageCollector.report(CompilerMessageSeverity.LOGGING, "KotlinxReflect: include = ${pkgFqName}.${cls}")
                            classesToRegisterForReflection.add(sym)
                            globRegexes[pkgName.toRegexFromGlob('.')] = Pair(pkgName, true)
                        } else {
                            messageCollector.report(CompilerMessageSeverity.LOGGING, "KotlinxReflect: exclude = ${pkgFqName}.${cls}")
                        }
                    }

                    // This used to work kotlin 1.9
//                    moduleFragment.descriptor.getPackage(pkgFqName).fragments.forEach { frag ->
//                        messageCollector.report(CompilerMessageSeverity.LOGGING, "KotlinxReflect: Checking content of package (2) '$pkgName'")
//                        frag.getMemberScope().getClassifierNames()?.forEach { cls ->
//                            val sym = pluginContext.referenceClass(ClassId(frag.fqName, cls))
//                            if (null != sym) {// && true==sym.signature?.isPubliclyVisible) {
//                                messageCollector.report(CompilerMessageSeverity.LOGGING, "KotlinxReflect: include = ${frag.fqName}.${cls}")
//                                classesToRegisterForReflection.add(sym)
//                                globRegexes[pkgName.toRegexFromGlob('.')] = Pair(pkgName, true)
//                            } else {
//                                messageCollector.report(CompilerMessageSeverity.LOGGING, "KotlinxReflect: exclude = ${frag.fqName}.${cls}")
//                            }
//                        }
//                    }


                }

                else -> Unit // TODO
            }
        }

        //val plgCtx = pluginContext as IrPluginContextImpl
        // val linker = plgCtx.linker as KotlinIrLinker

        /*
                moduleFragment.descriptor.allDependencyModules.forEach { md ->
                    val kotlinLibrary = (md.getCapability(KlibModuleOrigin.CAPABILITY) as? DeserializedKlibModuleOrigin)?.library

                        val irmd = linker.deserializeOnlyHeaderModule(md, kotlinLibrary)
                        ExternalDependenciesGenerator(symbolTable, listOf(linker))
                            .generateUnboundSymbolsAsDependencies()
                        linker.postProcess()
                        messageCollector.report(CompilerMessageSeverity.WARNING, "dependency = ${irmd.safeName}")
                        messageCollector.report(CompilerMessageSeverity.WARNING, "dependency.files = ${irmd.files.map { it.fqName }}")
                        irmd.acceptVoid(object : IrElementVisitorVoid {
                            override fun visitElement(element: IrElement) {
                                element.acceptChildrenVoid(this)
                            }

                            override fun visitClass(declaration: IrClass) {
                                super.visitClass(declaration)
                                if (declaration.kotlinFqName.asString().startsWith("kotlin").not()) {
                                    messageCollector.report(CompilerMessageSeverity.WARNING, "member.name = ${declaration.kotlinFqName}")
                                }
                                globRegexes.forEach { regex ->
                                    if (regex.matches(declaration.kotlinFqName.asString())) {
                                        classesToRegisterForReflection.add(declaration.symbol)
                                    }
                                }
                            }
                        })

                }

         */
        /*
                symbolTable.forEachPublicSymbol { sym ->
                    val fqname = sym.descriptor.fqNameSafe
                    if (fqname.asString().startsWith("kotlin").not()) {
                        messageCollector.report(CompilerMessageSeverity.WARNING, "checking = ${fqname}")
                        if (sym is IrClassSymbol) {
                            globRegexes.forEach { regex ->
                                if (regex.matches(fqname.asString())) {
                                    classesToRegisterForReflection.add(sym as IrClassSymbol)
                                }
                            }
                        }
                    }
                }
        */

        // Classes for Reflection from this module (using module  fragment visitor)
        var class_KotlixReflectForModule: IrClass? = null
        moduleFragment.acceptVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                super.visitClass(declaration)
                when {

                    declaration.visibility.isPublicAPI -> {
                        messageCollector.report(
                            CompilerMessageSeverity.LOGGING,
                            "KotlinxReflect: visiting ${declaration.kotlinFqName.asString()}"
                        )

                        if (declaration.kotlinFqName.asString() == kotlinxReflectRegisterForModuleClassFqName) {
                            class_KotlixReflectForModule = declaration
                        }

                        globRegexes.forEach { entry ->
                            val regex = entry.key
                            if (regex.matches(declaration.kotlinFqName.asString())) {
                                classesToRegisterForReflection.add(declaration.symbol)

                                if (isJs && declaration.isJsExport().not()) {
                                    messageCollector.report(
                                        CompilerMessageSeverity.STRONG_WARNING,
                                        "KotlinxReflect: declaration ${declaration.kotlinFqName.asString()} is used for reflection but is not exported. Make it public and add the package to 'exportPublic' configuration or (if you must) use @JsExport"
                                    )
                                }

                                if (isJvm && declaration.visibility.isPublicAPI.not()) {
                                    messageCollector.report(
                                        CompilerMessageSeverity.STRONG_WARNING,
                                        "KotlinxReflect: declaration ${declaration.kotlinFqName.asString()} is used for reflection but is not public. Make it public"
                                    )
                                }

                                globRegexes[regex] = Pair(entry.value.first, true)
                            }
                        }
                    }

                    else -> {
                        messageCollector.report(
                            CompilerMessageSeverity.LOGGING,
                            "KotlinxReflect: NOT visiting private ${declaration.kotlinFqName.asString()}"
                        )
                    }
                }
            }
        })

        globRegexes.forEach { entry ->
            if (entry.value.second) {
                //used
            } else {
                messageCollector.report(CompilerMessageSeverity.STRONG_WARNING, "KotlinxReflect: No matches for '${entry.value.first}' (Regex '${entry.key}') in module fragment '${moduleFragment.name}'")
            }
        }

        val krfm = class_KotlixReflectForModule
        if (null == krfm) {
            //can't validly report ERROR ot WARNING here as module is in multiple fragments
            // should report ERROR if no fragment finds the class
            //TODO: how to do that?
        } else {
            // val modulePackage = syntheticFile(moduleFragment, pluginContext)

            val generatedFor = buildKotlinxReflectModuleRegistry(
                pluginContext,
                krfm,
                classesToRegisterForReflection.toList()
            )
            val fun_classForNameAfterRegistration =
                krfm.getSimpleFunction(classForNameAfterRegistration)?.owner
                    ?: error("$classForNameAfterRegistration not found in generated class ${krfm.name.identifier}")
            moduleFragment.transform(
                KotlinxReflectRegisterTransformer(
                    messageCollector,
                    pluginContext,
                    krfm.symbol,
                    fun_classForNameAfterRegistration
                ), null
            )

            //messageCollector.report(CompilerMessageSeverity.LOGGING, moduleFragment.dump())
            messageCollector.report(CompilerMessageSeverity.LOGGING, moduleFragment.dumpKotlinLike())

            val outputMsg = generatedFor.groupBy { it.parent() }.entries.joinToString(separator = "\n") { "  ${it.key.asString()} - ${it.value.size}" }
            messageCollector.report(CompilerMessageSeverity.INFO, "Generated:\n${outputMsg}")
        }
    }

    /*
        fun registerUsedClasses(pluginContext: IrPluginContext, fun_registerUsedClasses:IrFunction, usedClasses: List<IrClass>) {
            messageCollector.report(CompilerMessageSeverity.WARNING, "registering classes $usedClasses")
            val classSym_ModuleRegistry = pluginContext.referenceClass(fq_KotlinxReflect) ?: error("Cannot find ModuleRegistry class")
            val funSym_registerClass = pluginContext.referenceFunctions(fq_registerClass).single() // should be only one

            val body = fun_registerUsedClasses.body!!
            val plgCtx = pluginContext as IrPluginContextImpl
            val reflexModuleDescriptor = classSym_ModuleRegistry.descriptor.module
            val reflexIrModule = (plgCtx.linker as KotlinIrLinker).deserializeFullModule(reflexModuleDescriptor,reflexModuleDescriptor.kotlinLibrary)
            var irclass_ModuleRegistry:IrClass? = null
            reflexIrModule.acceptVoid(object : IrElementVisitorVoid {
                    override fun visitElement(element: IrElement) {
                        element.acceptChildrenVoid(this)
                    }

                    override fun visitClass(declaration: IrClass) {
                        super.visitClass(declaration)
                        if (declaration.kotlinFqName == fq_KotlinxReflect) {
                            irclass_ModuleRegistry = declaration
                        }
                    }
                })


            messageCollector.report(CompilerMessageSeverity.WARNING, "registerUsedClasses should be deserialized")
            val newBody = DeclarationIrBuilder(pluginContext, fun_registerUsedClasses.symbol).irBlockBody {
                val obj = irGetObject(classSym_ModuleRegistry)
                val call = irCall(funSym_registerClass, obj.type)

                    for (statement in body.statements) {
                        +statement
                    }

                for (refCls in usedClasses) {
                    val refClsSym = refCls.symbol
                    val qn = irString(refCls.kotlinFqName.asString())
                    val cls = classReference(refClsSym, pluginContext.irBuiltIns.kClassClass.typeWith(refCls.defaultType))
                    call.dispatchReceiver = obj
                    call.putValueArgument(0, qn)
                    call.putValueArgument(1, cls)
                    +call
                }
            }
            messageCollector.report(CompilerMessageSeverity.WARNING, fun_registerUsedClasses.dump())
            messageCollector.report(CompilerMessageSeverity.WARNING, fun_registerUsedClasses.dumpKotlinLike())
            fun_registerUsedClasses.body = newBody
        }
    */
    private fun syntheticFile(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext): IrFile {
        val internalPackageFragmentDescriptor = EmptyPackageFragmentDescriptor(moduleFragment.descriptor.builtIns.builtInsModule, FqName(moduleFragment.safeName))
        return IrFileImpl(object : IrFileEntry {
            override val name = moduleFragment.safeName
            override val maxOffset = UNDEFINED_OFFSET

            override fun getSourceRangeInfo(beginOffset: Int, endOffset: Int) =
                SourceRangeInfo(
                    "",
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET
                )

            override fun getLineNumber(offset: Int) = UNDEFINED_OFFSET
            override fun getColumnNumber(offset: Int) = UNDEFINED_OFFSET
            override fun getLineAndColumnNumbers(offset: Int) = LineAndColumn(getLineNumber(offset), getColumnNumber(offset))

        }, internalPackageFragmentDescriptor, moduleFragment).also {
            moduleFragment.files += it
        }
    }

    /*
    // build the module specific KotlinxReflectModuleRegistry object
    fun buildKotlinxReflectModuleRegistry1(pluginContext: IrPluginContext, owningPackage: IrPackageFragment, classes: List<IrClassSymbol>): Pair<IrClass, IrSimpleFunction> {
        messageCollector.report(CompilerMessageSeverity.LOGGING, "registering classes $classes")

        val class_ModuleRegistry = pluginContext.referenceClass(fq_KotlinxReflect) ?: error("Cannot find ModuleRegistry class")
        val fun_registerClass = pluginContext.referenceFunctions(fq_registerClass).single() // should be only one
        val fun_classForName = pluginContext.referenceFunctions(fq_classForName).single() // should be only one

        val class_KotlixReflect = pluginContext.irFactory.buildClass {
            name = Name.identifier(KotlinxReflectRegisterForModuleClassName)
            modality = Modality.FINAL
            kind = ClassKind.OBJECT
        }
        class_KotlixReflect.superTypes = listOf(pluginContext.irBuiltIns.anyType)
        owningPackage.addChild(class_KotlixReflect)
        class_KotlixReflect.createImplicitParameterDeclarationWithWrappedDescriptor()
        val cons = class_KotlixReflect.addSimpleDelegatingConstructor(
            superConstructor = pluginContext.irBuiltIns.anyClass.owner.constructors.single(),
            irBuiltIns = pluginContext.irBuiltIns,
            isPrimary = true
        )
        cons.visibility = DescriptorVisibilities.PRIVATE
        val fun_registerClasses = class_KotlixReflect.addFunction(
            name = "registerClasses",
            visibility = DescriptorVisibilities.PRIVATE,
            returnType = pluginContext.irBuiltIns.unitType
        ).apply {
            body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
                val obj = irGetObject(class_ModuleRegistry)
                for (refCls in classes) {
                    val call = irCall(fun_registerClass, obj.type)
                    messageCollector.report(CompilerMessageSeverity.LOGGING, "sym = $refCls")
                    val refClsSym = refCls//.symbol
                    val qns = refCls.signature?.packageFqName()?.child(Name.identifier(refCls.signature?.asPublic()?.declarationFqName!!))
                    val qn = irString(qns!!.asString())
                    messageCollector.report(CompilerMessageSeverity.LOGGING, "qn = ${qns}")
                    val cls = classReference(refClsSym, pluginContext.irBuiltIns.kClassClass.typeWith(refClsSym.defaultType))
                    call.dispatchReceiver = obj
                    call.putValueArgument(0, qn)
                    call.putValueArgument(1, cls)
                    +call
                }
            }
        }

        val fun_classForNameAfterRegistration = class_KotlixReflect.addFunction(
            name = "classForNameAfterRegistration",
            returnType = pluginContext.irBuiltIns.kClassClass.starProjectedType,
            visibility = DescriptorVisibilities.INTERNAL
        ).apply {
            addValueParameter("qualifiedName", pluginContext.irBuiltIns.stringType)
            body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
                val c1 = irCall(fun_registerClasses)
                c1.dispatchReceiver = irGet(dispatchReceiverParameter!!)
                +c1
                val obj = irGetObject(class_ModuleRegistry)
                val call = irCall(fun_classForName, obj.type)
                val qn = irGet(valueParameters[0])
                call.dispatchReceiver = obj
                call.putValueArgument(0, qn)
                +irReturn(call)
            }
        }

        // Does not seem to be needed in 1.6.0-RC ?
        class_KotlixReflect.addFakeOverrides(IrTypeSystemContextImpl(pluginContext.irBuiltIns))
        return Pair(class_KotlixReflect, fun_classForNameAfterRegistration)
    }
    */

    /**
     * returns list of qualified names of classes generated for
     */
    @OptIn(ObsoleteDescriptorBasedAPI::class)
    fun buildKotlinxReflectModuleRegistry(pluginContext: IrPluginContext, class_KotlixReflectForModule: IrClass, classes: List<IrClassSymbol>): List<FqName> {
        // fun registerClass(
        // [0]   qualifiedName: String,
        // [1]  cls: KClass<*>,
        // [2]  valuesFunction: ValuesFunction? = null
        // [3]  ObjectInstance: Any? = null
        // )
        val results = mutableListOf<FqName>()
        messageCollector.report(CompilerMessageSeverity.LOGGING, "registering classes $classes")

        val class_KotlinxReflect = pluginContext.referenceClass(fq_KotlinxReflect) ?: error("Cannot find ModuleRegistry class '${fq_KotlinxReflect.asFqNameString()}'")
        val fun_registerClass = pluginContext.referenceFunctions(fq_registerClass).single() // should be only one

        val fun_classForNameAfterRegistration = class_KotlixReflectForModule.getSimpleFunction(registerUsedClasses)?.owner
            ?: error("$registerUsedClasses not found in generated class ${class_KotlixReflectForModule.name.identifier}")

        fun_classForNameAfterRegistration.body = DeclarationIrBuilder(pluginContext, class_KotlixReflectForModule.symbol).irBlockBody {
            val obj = irGetObject(class_KotlinxReflect)
            for (refCls in classes) {
                try {
                    val call = irCall(fun_registerClass, obj.type)
                    messageCollector.report(CompilerMessageSeverity.LOGGING, "sym = $refCls")
                    val refClsSym = refCls//.symbol
                    //val sig = refCls.signature ?: error("signature is 'null' for '${refCls}' '${refCls.starProjectedType.classFqName}'")
                    //val asp = refCls.signature?.asPublic() ?: error("signature.asPublic is 'null' for '${refCls}'")
                    //val fqname = refCls.signature?.asPublic()?.declarationFqName ?: error("declarationFqName is 'null' for '${refCls}' '${refCls.signature?.asPublic()?.firstNameSegment}'")
                    val fqname = refCls.starProjectedType.classFqName ?: error("No classFqName for '${refCls}'")
                    val qns = fqname //refCls.signature?.packageFqName()?.child(Name.identifier(fqname.asString()))
                    //if (null==qns) error("Cannot find '${fqname}'")
                    val qn = irString(qns.asString())
                    messageCollector.report(CompilerMessageSeverity.INFO, "registered class ${qns.asString()} with ${fq_KotlinxReflect.shortClassName.asString()}")
                    val cls = classReference(refClsSym, pluginContext.irBuiltIns.kClassClass.typeWith(refClsSym.defaultType))

                    call.dispatchReceiver = obj
                    call.arguments[1] = qn
                    call.arguments[2] = cls

                    when (refCls.descriptor.kind) {
                        ClassKind.ENUM_CLASS -> {
                            val valuesFun = refCls.getSimpleFunction("values") ?: error("No function 'values' defined for '${refCls}'")
                            //val valuesCall = irCall(valuesFun)
                            val valuesFunType = pluginContext.irBuiltIns.getKFunctionType(
                                pluginContext.irBuiltIns.listClass.starProjectedType,
                                emptyList()
                            )
                            val funRef = irFunctionReference(valuesFunType, valuesFun)
                            call.arguments[3] = funRef
                            call.arguments[4] = irNull()
                        }

                        ClassKind.OBJECT -> {
                            val instance = irGetObject(refCls)
                            call.arguments[3] = irNull()
                            call.arguments[4] = instance
                        }

                        else -> {
                            call.arguments[3] = irNull()
                            call.arguments[4] = irNull()
                        }
                    }
                    +call
                    results.add(fqname)
                } catch (t: Throwable) {
                    messageCollector.report(CompilerMessageSeverity.ERROR, "Error registering class ${refCls}")
                }
            }
        }
        return results
    }

}


class KotlinxReflectRegisterTransformer(
    private val messageCollector: MessageCollector,
    private val pluginContext: IrPluginContext,
    private val class_KotlixReflect_sym: IrClassSymbol,
    private val fun_classForNameAfterRegistration: IrFunction
) : IrElementTransformerVoidWithContext() {

    private val functionClassForName = pluginContext.referenceFunctions(KotlinxReflectIrGenerationExtension.fq_classForName).single() // should be only one

    override fun visitCall(expression: IrCall): IrExpression {
        //convert all ModuleRegistry.classForName calls EXCEPT the one in generated class KotlinxReflect
        val curClass_sym = (this.currentClass?.irElement as IrClass?)?.symbol
        if (expression.symbol == functionClassForName && curClass_sym != class_KotlixReflect_sym) {
            messageCollector.report(CompilerMessageSeverity.LOGGING, "called: ${expression.dumpKotlinLike()} on ${curClass_sym}")
            val curFun = this.currentFunction?.irElement as IrFunction?
            if (null != curFun) {
                val b = DeclarationIrBuilder(pluginContext, curFun.symbol)
                val call = b.irCall(fun_classForNameAfterRegistration)
                call.dispatchReceiver = b.irGetObject(class_KotlixReflect_sym)
                call.arguments[1] = expression.arguments[1]
                return call
            } else {
                TODO()
            }
        } else {
            return super.visitCall(expression)
        }
    }
}

