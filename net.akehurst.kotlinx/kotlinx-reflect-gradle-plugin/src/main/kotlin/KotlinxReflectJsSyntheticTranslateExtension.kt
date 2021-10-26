package net.akehurst.kotlinx.reflect.gradle.plugin

import org.jetbrains.kotlin.analyzer.moduleInfo
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.js.translate.context.TranslationContext
import org.jetbrains.kotlin.js.translate.declaration.DeclarationBodyVisitor
import org.jetbrains.kotlin.js.translate.extensions.JsSyntheticTranslateExtension
import org.jetbrains.kotlin.psi.KtPureClassOrObject

class KotlinxReflectJsSyntheticTranslateExtension(
    val messageCollector: MessageCollector,
    val forReflection:List<String>
): JsSyntheticTranslateExtension {

    override fun generateClassSyntheticParts(declaration: KtPureClassOrObject, descriptor: ClassDescriptor, translator: DeclarationBodyVisitor, context: TranslationContext) {
        messageCollector.report(CompilerMessageSeverity.WARNING,"generating synthetic parts of ${declaration.name}")
        messageCollector.report(CompilerMessageSeverity.WARNING,"Modules")
        for(it in context.config.libraries) {
            messageCollector.report(CompilerMessageSeverity.WARNING,"$it")
        }

        val refLibs = forReflection.filterNot(String::isNullOrBlank)

        messageCollector.report(CompilerMessageSeverity.WARNING,"defaultReflectionLibs")
        for(it in refLibs) {
            messageCollector.report(CompilerMessageSeverity.WARNING,"$it")
        }


    }


}