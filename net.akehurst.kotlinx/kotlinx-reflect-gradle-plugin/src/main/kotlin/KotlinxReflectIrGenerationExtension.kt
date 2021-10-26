package net.akehurst.kotlinx.reflect.gradle.plugin

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
import org.jetbrains.kotlin.descriptors.konan.kotlinLibrary
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.isJsExport
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.module

class KotlinxReflectIrGenerationExtension(
    private val messageCollector: MessageCollector,
    val forReflection: List<String>
) : IrGenerationExtension {

    companion object {
        val fq_ModuleRegistry = FqName("net.akehurst.kotlinx.reflect.ModuleRegistry")
        val fq_registerClass = FqName("net.akehurst.kotlinx.reflect.ModuleRegistry.registerClass")
        val fq_registerUsedClasses = FqName("net.akehurst.kotlinx.reflect.ModuleRegistry.registerUsedClasses")
    }


    private val globRegexes = forReflection.mapNotNull {
        if (it.isBlank()) {
            messageCollector.report(CompilerMessageSeverity.WARNING, "InternalError: Got and ignored null or blank class name!")
            null
        } else {
            it.toRegexFromGlob('.')
        }
    }

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        messageCollector.report(CompilerMessageSeverity.WARNING, "forReflection = $forReflection")
        messageCollector.report(CompilerMessageSeverity.WARNING, "moduleFragment.files = ${moduleFragment.files.map { it.fqName }}")

        var irclass_ModuleRegistry:IrClass? = null
        val usedClasses = mutableListOf<IrClass>()
        lateinit var packageFragment: IrPackageFragment

        //pluginContext.

        moduleFragment.acceptVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitPackageFragment(declaration: IrPackageFragment) {
                super.visitPackageFragment(declaration)
                packageFragment = declaration
            }

            override fun visitClass(declaration: IrClass) {
                super.visitClass(declaration)
                if (declaration.kotlinFqName == fq_ModuleRegistry) {
                    irclass_ModuleRegistry = declaration
                }
                globRegexes.forEach { regex ->
                    if (regex.matches( declaration.kotlinFqName.asString() ) ) {
                        usedClasses.add(declaration)
                    }
                }
            }
        })
        val f = pluginContext.referenceFunctions(fq_registerUsedClasses).single().owner
        registerUsedClasses(pluginContext, f, usedClasses)

    }

    fun registerUsedClasses(pluginContext: IrPluginContext, fun_registerUsedClasses:IrFunction, usedClasses: List<IrClass>) {
        messageCollector.report(CompilerMessageSeverity.WARNING, "registering classes $usedClasses")
        val classSym_ModuleRegistry = pluginContext.referenceClass(fq_ModuleRegistry) ?: error("Cannot find ModuleRegistry class")
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
                    if (declaration.kotlinFqName == fq_ModuleRegistry) {
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
}


class KotlinxReflectRegisterTransformer(
    private val messageCollector: MessageCollector,
    private val pluginContext: IrPluginContext,
    private val class_KotlixReflect_sym: IrClassSymbol,
    private val fun_classForNameAfterRegistration: IrFunction
) : IrElementTransformerVoidWithContext() {

    companion object {
        const val classForName_name = "net.akehurst.kotlinx.reflect.ModuleRegistry.classForName"
    }

    private val functionClassForName = pluginContext.referenceFunctions(FqName(classForName_name)).single() // should be only one

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