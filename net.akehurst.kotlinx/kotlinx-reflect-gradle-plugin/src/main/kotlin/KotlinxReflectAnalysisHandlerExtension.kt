package net.akehurst.kotlinx.reflect.gradle.plugin

import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.js.analyzer.JsAnalysisResult
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension

class KotlinxReflectAnalysisHandlerExtension(
    private val messageCollector: MessageCollector,
    val forReflection:String
) : AnalysisHandlerExtension {

    override fun doAnalysis(
        project: Project,
        module: ModuleDescriptor,
        projectContext: ProjectContext,
        files: Collection<KtFile>,
        bindingTrace: BindingTrace,
        componentProvider: ComponentProvider
    ): AnalysisResult? {
        return super.doAnalysis(project, module, projectContext, files, bindingTrace, componentProvider)
    }

    override fun analysisCompleted(project: Project, module: ModuleDescriptor, bindingTrace: BindingTrace, files: Collection<KtFile>): AnalysisResult? {

       val result = super.analysisCompleted(project, module, bindingTrace, files)

        messageCollector.report(CompilerMessageSeverity.WARNING,"analysisCompleted ${result?.let{it::class.simpleName}?:"null"}")

        return when(result) {
            is JsAnalysisResult -> {
                messageCollector.report(CompilerMessageSeverity.WARNING,"Result ${result}")
                result
            }
            else -> result
        }
    }

}