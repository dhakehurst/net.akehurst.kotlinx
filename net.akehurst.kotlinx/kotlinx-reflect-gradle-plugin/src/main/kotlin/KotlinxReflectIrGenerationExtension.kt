package net.akehurst.kotlinx.reflect.gradle.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class KotlinxReflectIrGenerationExtension(
    private val messageCollector: MessageCollector,
    private val string: String,
    private val file: String
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        messageCollector.report(CompilerMessageSeverity.WARNING, "Argument 'string' = $string")
        messageCollector.report(CompilerMessageSeverity.WARNING, "Argument 'file' = $file")
    }
}