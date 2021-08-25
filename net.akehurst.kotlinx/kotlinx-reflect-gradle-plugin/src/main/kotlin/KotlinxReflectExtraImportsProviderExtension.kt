package net.akehurst.kotlinx.reflect.gradle.plugin

import org.jetbrains.kotlin.analyzer.moduleInfo
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.js.translate.context.TranslationContext
import org.jetbrains.kotlin.js.translate.declaration.DeclarationBodyVisitor
import org.jetbrains.kotlin.js.translate.extensions.JsSyntheticTranslateExtension
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportInfo
import org.jetbrains.kotlin.psi.KtPureClassOrObject
import org.jetbrains.kotlin.resolve.extensions.ExtraImportsProviderExtension

class KotlinxReflectExtraImportsProviderExtension(
    val messageCollector: MessageCollector,
    val forReflection:String
): ExtraImportsProviderExtension {

    override fun getExtraImports(ktFile: KtFile): Collection<KtImportInfo> {
        messageCollector.report(CompilerMessageSeverity.WARNING,"getExtraImports $ktFile")
        val imports = forReflection.split(java.io.File.pathSeparator)
        val infos = imports.map {
            object : KtImportInfo{
                override val aliasName: String? = null
                override val importedFqName: FqName = FqName(it)
                override val importContent: KtImportInfo.ImportContent = KtImportInfo.ImportContent.FqNameBased(importedFqName)
                override val isAllUnder: Boolean = false

            }
        }
        messageCollector.report(CompilerMessageSeverity.WARNING,"importedNames ${infos.map{it.importedName}}")
        return infos
    }

}