package net.akehurst.kotlinx.reflect.gradle.plugin

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.*
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.isJsExport
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class KotlinxReflectIrGenerationExtension(
    private val messageCollector: MessageCollector,
    val forReflection: String
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        messageCollector.report(CompilerMessageSeverity.WARNING, "forReflection = $forReflection")
        messageCollector.report(CompilerMessageSeverity.WARNING, "forReflection = ${moduleFragment.files.map { it.fqName }}")

        lateinit var packageFragment: IrPackageFragment
        moduleFragment.acceptVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitPackageFragment(declaration: IrPackageFragment) {
                super.visitPackageFragment(declaration)
                packageFragment = declaration
            }

        })


        val (class_KotlixReflect, fun_classForNameAfterRegistration) = buildKotlinxReflectObject(
            pluginContext,
            packageFragment,
            listOf("net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.A")
        )

        /*
        val cls = pluginContext.irFactory.buildClass {
            name = Name.identifier("KotlinxReflect")
            modality = Modality.FINAL
            kind = ClassKind.OBJECT
        }
        cls.superTypes = listOf(pluginContext.irBuiltIns.anyType)
        packageFragment.addChild(cls)
        cls.createImplicitParameterDeclarationWithWrappedDescriptor()
        val cons = cls.addSimpleDelegatingConstructor(
            superConstructor = pluginContext.irBuiltIns.anyClass.owner.constructors.single(),
            irBuiltIns = pluginContext.irBuiltIns,
            isPrimary = true
        )
        cons.visibility = DescriptorVisibilities.PRIVATE

        cls.addAnonymousInitializer().also { irInitializer ->
            irInitializer.body =
                DeclarationIrBuilder(pluginContext, irInitializer.symbol).irBlockBody {
                    val obj = irGetObject(classModuleRegistry)
                    val call = irCall(functionRegisterClass, obj.type)
                    val qn = irString("net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.A")
                    val cls = kClassReference(refCls.typeWith())
                    call.dispatchReceiver = obj
                    call.putValueArgument(0, qn)
                    call.putValueArgument(1, cls)
                    +call
                }
        }
        cls.addFakeOverrides(pluginContext.irBuiltIns)
        cls.patchDeclarationParents(cls.parent)
         */

        moduleFragment.transform(
            KotlinxReflectRegisterTransformer(
                messageCollector,
                pluginContext,
                class_KotlixReflect,
                fun_classForNameAfterRegistration
            ), null
        )

        messageCollector.report(CompilerMessageSeverity.WARNING, moduleFragment.dump())
        messageCollector.report(CompilerMessageSeverity.WARNING, moduleFragment.dumpKotlinLike())

    }


}

fun buildKotlinxReflectObject(pluginContext: IrPluginContext, pkgOwner: IrPackageFragment, classes: List<String>): Pair<IrClass, IrSimpleFunction> {
    val class_ModuleRegistry = pluginContext.referenceClass(FqName("net.akehurst.kotlinx.reflect.ModuleRegistry")) ?: error("Cannot find ModuleRegistry class")
    val fun_registerClass = pluginContext.referenceFunctions(FqName("net.akehurst.kotlinx.reflect.ModuleRegistry.registerClass")).single() // should be only one
    val fun_classForName = pluginContext.referenceFunctions(FqName("net.akehurst.kotlinx.reflect.ModuleRegistry.classForName")).single() // should be only one

    val class_KotlixReflect = pluginContext.irFactory.buildClass {
        name = Name.identifier("KotlinxReflect")
        modality = Modality.FINAL
        kind = ClassKind.OBJECT
    }
    class_KotlixReflect.superTypes = listOf(pluginContext.irBuiltIns.anyType)
    pkgOwner.addChild(class_KotlixReflect)
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
            for (refClsName in classes) {
                val refCls = pluginContext.referenceClass(FqName(refClsName))!!
                val qn = irString(refClsName)
                val cls = classReference(refCls, pluginContext.irBuiltIns.kClassClass.typeWith(refCls.defaultType))
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

class KotlinxReflectRegisterTransformer(
    private val messageCollector: MessageCollector,
    private val pluginContext: IrPluginContext,
    private val class_KotlixReflect: IrClass,
    private val fun_classForNameAfterRegistration: IrFunction
) : IrElementTransformerVoidWithContext() {

    companion object {
        const val classForName_name = "net.akehurst.kotlinx.reflect.ModuleRegistry.classForName"
    }

    private val functionClassForName = pluginContext.referenceFunctions(FqName(classForName_name)).single() // should be only one

    override fun visitCall(expression: IrCall): IrExpression {
        //convert all ModuleRegistry.classForName calls EXCEPT the one in generated class KotlixReflect
        val curClass = this.currentClass?.irElement as IrClass
        if (expression.symbol == functionClassForName && curClass != class_KotlixReflect) {
            messageCollector.report(CompilerMessageSeverity.WARNING, "called: ${expression.dumpKotlinLike()} on ${curClass.dumpKotlinLike()}")
            val curFun = this.currentFunction?.irElement as IrFunction?
            if (null != curFun) {
                val b = DeclarationIrBuilder(pluginContext, curFun.symbol)
                val call = b.irCall(fun_classForNameAfterRegistration)
                call.dispatchReceiver = b.irGetObject(class_KotlixReflect.symbol)
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