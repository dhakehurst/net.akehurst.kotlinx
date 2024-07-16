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
    val forReflection:List<String>
): ExtraImportsProviderExtension {

    override fun getExtraImports(ktFile: KtFile): Collection<KtImportInfo> {
        messageCollector.report(CompilerMessageSeverity.INFO,"getExtraImports for $ktFile")
        val imports = forReflection
        val infos = imports.map {
            val fqname = FqName(it)
            object : KtImportInfo{
                override val aliasName: String? = null
                override val importedFqName: FqName = fqname
                override val importContent: KtImportInfo.ImportContent = KtImportInfo.ImportContent.FqNameBased(fqname)
                override val isAllUnder: Boolean = true

            }
        }
        messageCollector.report(CompilerMessageSeverity.INFO,"importedNames ${infos.map{it.importedFqName}}")
        return infos
    }

}