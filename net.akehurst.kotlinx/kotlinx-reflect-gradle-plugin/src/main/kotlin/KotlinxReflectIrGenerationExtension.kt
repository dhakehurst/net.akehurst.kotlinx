package net.akehurst.kotlinx.reflect.gradle.plugin

import net.akehurst.kotlinx.reflect.KotlinxReflect
import org.jetbrains.kotlin.analyzer.moduleInfo
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContextImpl
import org.jetbrains.kotlin.backend.common.ir.*
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.serialization.KotlinIrLinker
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.impl.EmptyPackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.konan.kotlinLibrary
import org.jetbrains.kotlin.ir.*
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.safeName
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrModuleFragmentImpl
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.library.IrLibrary
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.module

class KotlinxReflectIrGenerationExtension(
    private val messageCollector: MessageCollector,
    val forReflection: List<String>
) : IrGenerationExtension {

    companion object {
        val fq_KotlinxReflect = FqName(KotlinxReflect::class.qualifiedName!!)
        val fq_registerClass = FqName("${fq_KotlinxReflect}.registerClass")
        val fq_registerUsedClasses = FqName("${fq_KotlinxReflect}.registerUsedClasses")
        val fq_classForName = FqName("${fq_KotlinxReflect}.classForName")
        val KotlinxReflectRegisterForModuleClassName = "KotlinxReflectRegisterForModule"
    }

    private val globRegexes = forReflection.mapNotNull {
        if (it.isBlank()) {
            messageCollector.report(CompilerMessageSeverity.WARNING, "InternalError: Got and ignored null or blank class name!")
            null
        } else {
            it.toRegexFromGlob('.')
        }
    }

    @OptIn(ObsoleteDescriptorBasedAPI::class)
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        messageCollector.report(CompilerMessageSeverity.LOGGING, "moduleFragment.name = ${moduleFragment.name}")
        messageCollector.report(CompilerMessageSeverity.LOGGING, "moduleFragment.safeName = ${moduleFragment.safeName}")
        messageCollector.report(CompilerMessageSeverity.WARNING, "forReflection = $forReflection")
        messageCollector.report(CompilerMessageSeverity.WARNING, "moduleFragment.files = ${moduleFragment.files.map { it.fqName }}")

        val classesToRegisterForReflection = mutableSetOf<IrClassSymbol>()

        (pluginContext.symbolTable as SymbolTable).forEachPublicSymbol { sym ->
            val fqname = sym.descriptor.fqNameSafe
            messageCollector.report(CompilerMessageSeverity.LOGGING, "checking = ${fqname}")
            if (null!=fqname) {
                globRegexes.forEach { regex ->
                    if (regex.matches(fqname.asString())) {
                        classesToRegisterForReflection.add(sym as IrClassSymbol)
                    }
                }
            }
        }

        //not sure we need to do this
        moduleFragment.acceptVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                super.visitClass(declaration)

                globRegexes.forEach { regex ->
                    if (regex.matches( declaration.kotlinFqName.asString() ) ) {
                        classesToRegisterForReflection.add(declaration.symbol)
                    }
                }
            }
        })

        //TODO: find KotlinexReflectForModule in all depended on modules, call registerclasses on them

        val modulePackage = syntheticFile(moduleFragment,pluginContext)

        val (class_KotlixReflect, fun_classForNameAfterRegistration) = buildKotlinxReflectModuleRegistry(
            pluginContext,
            modulePackage,
            classesToRegisterForReflection.toList()
        )
        moduleFragment.transform(
            KotlinxReflectRegisterTransformer(
                messageCollector,
                pluginContext,
                class_KotlixReflect.symbol,
                fun_classForNameAfterRegistration
            ), null
        )

        messageCollector.report(CompilerMessageSeverity.LOGGING, moduleFragment.dump())
        messageCollector.report(CompilerMessageSeverity.LOGGING, moduleFragment.dumpKotlinLike())


        //val f = pluginContext.referenceFunctions(fq_registerUsedClasses).single().owner
        //registerUsedClasses(pluginContext, f, usedClasses)

    }

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

    private fun syntheticFile(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext): IrFile {
        val internalPackageFragmentDescriptor = EmptyPackageFragmentDescriptor(pluginContext.builtIns.builtInsModule, FqName(moduleFragment.safeName))
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
        }, internalPackageFragmentDescriptor, moduleFragment).also {
            moduleFragment.files += it
        }
    }

    // build the module specific KotlinxReflectModuleRegistry object
    fun buildKotlinxReflectModuleRegistry(pluginContext: IrPluginContext, owningPackage: IrPackageFragment, classes: List<IrClassSymbol>): Pair<IrClass, IrSimpleFunction> {
        messageCollector.report(CompilerMessageSeverity.WARNING, "registering classes $classes")

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
                val call = irCall(fun_registerClass, obj.type)
                for (refCls in classes) {
                    val refClsSym = refCls//.symbol
                    val qn = irString(refCls.descriptor.fqNameSafe.asString())
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
                //+call
                +irReturn(call)
            }
        }
        //fun_registerClasses.parent = class_KotlixReflect
        //fun_classForNameAfterRegistration.parent = class_KotlixReflect
        //class_KotlixReflect.addChild(fun_registerClasses)
        //class_KotlixReflect.addChild(fun_classForNameAfterRegistration)
        class_KotlixReflect.addFakeOverrides(pluginContext.irBuiltIns)
        return Pair(class_KotlixReflect, fun_classForNameAfterRegistration)
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
        //convert all ModuleRegistry.classForName calls EXCEPT the one in generated class KotlixReflect
        val curClass_sym = (this.currentClass?.irElement as IrClass?)?.symbol
        if (expression.symbol == functionClassForName && curClass_sym != class_KotlixReflect_sym) {
            messageCollector.report(CompilerMessageSeverity.LOGGING, "called: ${expression.dumpKotlinLike()} on ${curClass_sym}")
            val curFun = this.currentFunction?.irElement as IrFunction?
            if (null != curFun) {
                val b = DeclarationIrBuilder(pluginContext, curFun.symbol)
                val call = b.irCall(fun_classForNameAfterRegistration)
                call.dispatchReceiver = b.irGetObject(class_KotlixReflect_sym)
                call.putValueArgument(0, expression.getValueArgument(0))
                return call
            } else {
                TODO()
            }
        } else {
            return super.visitCall(expression)
        }
    }
}

