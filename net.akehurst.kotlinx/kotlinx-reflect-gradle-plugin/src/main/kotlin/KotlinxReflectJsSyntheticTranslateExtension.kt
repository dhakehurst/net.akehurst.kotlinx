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
    val messageCollector: MessageCollector
): JsSyntheticTranslateExtension {

    override fun generateClassSyntheticParts(declaration: KtPureClassOrObject, descriptor: ClassDescriptor, translator: DeclarationBodyVisitor, context: TranslationContext) {
        println(context.currentModule.allDependencyModules)
        messageCollector.report(CompilerMessageSeverity.INFO,"KotlinxReflect")
        messageCollector.report(CompilerMessageSeverity.INFO,"Modules")
        messageCollector.report(CompilerMessageSeverity.INFO,"${context.currentModule.allDependencyModules}")
    }

}