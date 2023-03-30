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
import org.jetbrains.kotlin.cli.jvm.config.jvmModularRoots
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.ir.backend.js.jsResolveLibraries
import org.jetbrains.kotlin.ir.backend.js.moduleName
import org.jetbrains.kotlin.ir.backend.js.resolverLogger
import org.jetbrains.kotlin.ir.util.IrMessageLogger
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.js.translate.extensions.JsSyntheticTranslateExtension
import org.jetbrains.kotlin.resolve.extensions.ExtraImportsProviderExtension
import java.io.File

class KotlinxReflectGradlePlugin : KotlinCompilerPluginSupportPlugin {

    companion object {
        const val KotlinxReflectRegisterForModuleClassName = "KotlinxReflectForModule"
    }

    private lateinit var logger: Logger

    private lateinit var genDir: File
    private lateinit var kotlinxReflectRegisterForModuleClassFqName: String

    override fun getCompilerPluginId(): String = KotlinPluginInfo.KOTLIN_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = KotlinPluginInfo.PROJECT_GROUP,
        artifactId = KotlinPluginInfo.PROJECT_NAME,
        version = KotlinPluginInfo.PROJECT_VERSION
    )

    override fun getPluginArtifactForNative(): SubpluginArtifact = SubpluginArtifact(
        groupId = KotlinPluginInfo.PROJECT_GROUP,
        artifactId = KotlinPluginInfo.PROJECT_NAME + "-native",
        version = KotlinPluginInfo.PROJECT_VERSION
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun apply(target: Project) {
        val ext = target.extensions.create(KotlinxReflectGradlePluginExtension.NAME, KotlinxReflectGradlePluginExtension::class.java)
        target.configurations.create("forReflection")
        this.logger = target.logger

        val moduleSafeName = target.name.replace(Regex("[^a-zA-Z0-9]"), "_")
        this.kotlinxReflectRegisterForModuleClassFqName = "$moduleSafeName.${KotlinxReflectRegisterForModuleClassName}"

        val kotlinExtension = target.extensions.getByName("kotlin") as KotlinMultiplatformExtension
        val kotlinSourceSets = kotlinExtension.sourceSets
        this.genDir = File(target.buildDir, "kotlinxReflect/genSrc/commonMain")
        genDir.mkdirs()
        kotlinSourceSets.getByName("commonMain") {
            it.kotlin.srcDir(genDir)
        }

        kotlinSourceSets.forEach { ss ->
            if (ss.name.endsWith("Test")) {
                //do nothing
            } else {
                // we want to use the class from 'common' code
                // but we cannot 'rewrite' the body content
                if (ss.name.startsWith("common")) {
                    val moduleFile = File(genDir, "KotlinxReflectForModule.kt")
                    moduleFile.createNewFile()
                    moduleFile.printWriter().use { pw ->
                        pw.println(
                            """
                            // ${ss.name}
                            package $moduleSafeName
                            import net.akehurst.kotlinx.reflect.KotlinxReflect
                            import net.akehurst.language.api.processor.LanguageIssueKind
                            import net.akehurst.kotlinx.reflect.EnumValuesFunction
                            object $KotlinxReflectRegisterForModuleClassName {
                                 internal fun registerUsedClasses() { /* populated by IR generation */
                                    KotlinxReflect.registerClass(qualifiedName = "net.akehurst.language.api.processor.LanguageIssueKind", cls = LanguageIssueKind::class, enumValuesFunction = LanguageIssueKind::values as EnumValuesFunction)
                                  }
                                 internal fun classForNameAfterRegistration(qualifiedName: String): kotlin.reflect.KClass<*> {
                                  this.registerUsedClasses()
                                  return net.akehurst.kotlinx.reflect.KotlinxReflect.classForName(qualifiedName = qualifiedName)
                                }
                            }
                            """.trimIndent()
                        )
                    }
                } else {

                }
            }
        }
    }


    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension: KotlinxReflectGradlePluginExtension = project.extensions.getByType(KotlinxReflectGradlePluginExtension::class.java)
        // TODO: ensure kotlinx-reflect lib is a dependency

        // get classes required for reflection (TODO: can we deduce this from code analysis)
        //val forReflection = project.configurations.getByName("forReflection")

        //TODO: remove kotlin stdlib stuff
        val forRefFiles = extension.forReflection.get().distinct().let {
            if (it.isNotEmpty())
                it.joinToString(java.io.File.pathSeparator) else
                null
        } ?: ""
        logger.debug("To compiler, forReflection = $forRefFiles")
        return project.provider {
            listOf(
                SubpluginOption(
                    key = KotlinxReflectCommandLineProcessor.OPTION_kotlinxReflectRegisterForModuleClassFqName,
                    value = this.kotlinxReflectRegisterForModuleClassFqName
                ),
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
        const val OPTION_kotlinxReflectRegisterForModuleClassFqName = "kotlinxReflectRegisterForModuleClassFqName"
        val ARG_forReflection = CompilerConfigurationKey<String>(OPTION_forReflection)
        val ARG_kotlinxReflectRegisterForModuleClassFqName = CompilerConfigurationKey<String>(OPTION_kotlinxReflectRegisterForModuleClassFqName)
    }

    override val pluginId: String = KotlinPluginInfo.KOTLIN_PLUGIN_ID

    override val pluginOptions: Collection<CliOption> = listOf(
        CliOption(
            optionName = OPTION_forReflection,
            valueDescription = "string",
            description = "list of libraries for reflection access",
            required = false,
        ),
        CliOption(
            optionName = OPTION_kotlinxReflectRegisterForModuleClassFqName,
            valueDescription = "string",
            description = "name of the class/object that is the Reflection Registry for this module & compilation",
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
            OPTION_kotlinxReflectRegisterForModuleClassFqName -> configuration.put(ARG_kotlinxReflectRegisterForModuleClassFqName, value)
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

        messageCollector.report(CompilerMessageSeverity.LOGGING, "modularRoot = ${configuration.jvmModularRoots}")

        val forReflection = configuration.get(KotlinxReflectCommandLineProcessor.ARG_forReflection, defaultReflectionLibs)
            .split(java.io.File.pathSeparator).toList().filterNot { it.isNullOrBlank() }

        val kotlinxReflectRegisterForModuleClassFqName = configuration.get(KotlinxReflectCommandLineProcessor.ARG_kotlinxReflectRegisterForModuleClassFqName, defaultReflectionLibs)

        messageCollector.report(CompilerMessageSeverity.LOGGING, "configuration = ${configuration}")
        val dependencies = configuration.get(JSConfigurationKeys.LIBRARIES)
        if (null != dependencies) {
            val allResolvedDependencies = jsResolveLibraries(
                dependencies,
                configuration[JSConfigurationKeys.REPOSITORIES] ?: emptyList(),
                configuration.resolverLogger
            )
            allResolvedDependencies.forEach { kotlinLibrary, packageAccessHandler ->
                messageCollector.report(CompilerMessageSeverity.LOGGING, "moduleName = ${kotlinLibrary.moduleName}")
                if ("kotlin" != kotlinLibrary.moduleName) {
                    packageAccessHandler.loadModuleHeader(kotlinLibrary).packageFragmentNameList.forEach {
                        messageCollector.report(CompilerMessageSeverity.LOGGING, "packageFragmentName = ${it}")
                    }
                }
            }
        }
        //JsSyntheticTranslateExtension.registerExtension(project, KotlinxReflectJsSyntheticTranslateExtension(messageCollector, forReflection))
        //AnalysisHandlerExtension.registerExtension(project, KotlinxReflectAnalysisHandlerExtension(messageCollector, forReflection))
        ExtraImportsProviderExtension.registerExtension(project, KotlinxReflectExtraImportsProviderExtension(messageCollector, forReflection))
        IrGenerationExtension.registerExtension(project, KotlinxReflectIrGenerationExtension(messageCollector, kotlinxReflectRegisterForModuleClassFqName, forReflection))

    }

}

