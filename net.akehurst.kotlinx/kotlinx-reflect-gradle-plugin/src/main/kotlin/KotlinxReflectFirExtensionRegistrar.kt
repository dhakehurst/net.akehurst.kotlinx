package net.akehurst.kotlinx.reflect.gradle.plugin

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

class KotlinxReflectFirExtensionRegistrar(
   val messageCollector: MessageCollector,
   val kotlinxReflectRegisterForModuleClassFqName: String,
   val forReflection: List<String>
) : FirExtensionRegistrar() {

    override fun ExtensionRegistrarContext.configurePlugin() {
        messageCollector.report(CompilerMessageSeverity.LOGGING, "KotlinxReflect-FIR: ExtensionRegistrarContext.configurePlugin")
        +{ it:FirSession -> KotlinxReflectFirDeclarationGenerationExtension(it, messageCollector, kotlinxReflectRegisterForModuleClassFqName, forReflection) }
    }

}

