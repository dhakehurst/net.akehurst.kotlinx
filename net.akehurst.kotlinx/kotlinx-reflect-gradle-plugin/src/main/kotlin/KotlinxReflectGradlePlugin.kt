package net.akehurst.kotlinx.reflect.gradle.plugin

import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.js.translate.extensions.JsSyntheticTranslateExtension
import org.jetbrains.kotlin.resolve.extensions.ExtraImportsProviderExtension
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension

class KotlinxReflectGradlePlugin : KotlinCompilerPluginSupportPlugin {

    private lateinit var logger: Logger

    override fun getCompilerPluginId(): String = KotlinPluginInfo.KOTLIN_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = KotlinPluginInfo.PROJECT_GROUP,
        artifactId = KotlinPluginInfo.PROJECT_NAME,
        version = KotlinPluginInfo.PROJECT_VERSION
    )

    override fun getPluginArtifactForNative(): SubpluginArtifact = SubpluginArtifact(
        groupId = KotlinPluginInfo.PROJECT_GROUP,
        artifactId = KotlinPluginInfo.PROJECT_NAME+"-native",
        version = KotlinPluginInfo.PROJECT_VERSION
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean  = true

    override fun apply(target: Project) {
        val ext = target.extensions.create(KotlinxReflectGradlePluginExtension.NAME, KotlinxReflectGradlePluginExtension::class.java)
        target.configurations.create("forReflection")
        this.logger=target.logger
    }


    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension:KotlinxReflectGradlePluginExtension = project.extensions.getByType(KotlinxReflectGradlePluginExtension::class.java)
        // TODO: ensure kotlinx-reflect lib is a dependency


        // get classes required for reflection (TODO: can we deduce this from code analysis)
        //val forReflection = project.configurations.getByName("forReflection")

        //TODO: remove kotlin stdlib stuff
        val forRefFiles=extension.forReflection.get().distinct().let {
            if (it.isNotEmpty())
                it.joinToString(java.io.File.pathSeparator) else
                null
        } ?: ""
        logger.debug("To compiler, forReflection = $forRefFiles")
        return project.provider {
            listOf(
                SubpluginOption(key = KotlinxReflectCommandLineProcessor.OPTION_forReflection, value = forRefFiles)
            )
        }
    }

}

open class KotlinxReflectGradlePluginExtension(objects: ObjectFactory) {

    companion object {
        val NAME = "kotlinxReflect"
    }

    val forReflection = objects.listProperty(String::class.java)

}

@AutoService(CommandLineProcessor::class)
class KotlinxReflectCommandLineProcessor : CommandLineProcessor {
    companion object {
         const val OPTION_forReflection = "forReflection"
        val ARG_forReflection = CompilerConfigurationKey<String>(OPTION_forReflection)
    }

    override val pluginId: String = KotlinPluginInfo.KOTLIN_PLUGIN_ID

    override val pluginOptions: Collection<CliOption> = listOf(
        CliOption(
            optionName = OPTION_forReflection,
            valueDescription = "string",
            description = "list of libraries for reflection access",
            required = false,
        )
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        return when (option.optionName) {
            OPTION_forReflection -> configuration.put(ARG_forReflection, value)
            else -> throw IllegalArgumentException("Unexpected config option ${option.optionName}")
        }
    }
}

@AutoService(ComponentRegistrar::class)
class KotlinxReflectComponentRegistrar(
    private val defaultReflectionLibs: String
) : ComponentRegistrar {

    @Suppress("unused") // Used by service loader
    constructor() : this(
        defaultReflectionLibs = ""
    )

    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        val forReflection = configuration.get(KotlinxReflectCommandLineProcessor.ARG_forReflection, defaultReflectionLibs).split(java.io.File.pathSeparator).toList()

        //JsSyntheticTranslateExtension.registerExtension(project, KotlinxReflectJsSyntheticTranslateExtension(messageCollector, forReflection))
        //AnalysisHandlerExtension.registerExtension(project, KotlinxReflectAnalysisHandlerExtension(messageCollector, forReflection))
        ExtraImportsProviderExtension.registerExtension(project, KotlinxReflectExtraImportsProviderExtension(messageCollector, forReflection))
        IrGenerationExtension.registerExtension(project, KotlinxReflectIrGenerationExtension(messageCollector, forReflection))

    }

}

