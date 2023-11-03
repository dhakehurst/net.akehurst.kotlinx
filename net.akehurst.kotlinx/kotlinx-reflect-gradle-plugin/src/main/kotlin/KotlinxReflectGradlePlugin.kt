package net.akehurst.kotlinx.reflect.gradle.plugin

import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.config.jvmModularRoots
import org.jetbrains.kotlin.compiler.plugin.*
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.resolve.extensions.ExtraImportsProviderExtension
import java.io.File

class KotlinxReflectGradlePlugin : KotlinCompilerPluginSupportPlugin {

    companion object {
        const val KotlinxReflectRegisterForModuleClassName = "KotlinxReflectForModule"
        fun KotlinxReflectRegisterForModuleTemplate(moduleName: String, packageName: String) = """  
            // ${moduleName}
            package $packageName
            import net.akehurst.kotlinx.reflect.KotlinxReflect
            import net.akehurst.kotlinx.reflect.EnumValuesFunction
            object $KotlinxReflectRegisterForModuleClassName {
              internal fun registerUsedClasses() { /* populated by IR generation */
              }
              internal fun classForNameAfterRegistration(qualifiedName: String): kotlin.reflect.KClass<*> {
                this.registerUsedClasses()
                return net.akehurst.kotlinx.reflect.KotlinxReflect.classForName(qualifiedName = qualifiedName)
              }
            }
        """.trimIndent()
    }

    private lateinit var logger: Logger

    private lateinit var kotlinxReflectRegisterForModuleClassFqNameMain: String
    private lateinit var kotlinxReflectRegisterForModuleClassFqNameTest: String
    private var forReflectionMainStr = ""
    private var forReflectionTestStr = ""

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

    override fun apply(project: Project) {
        //Note: the values assigned to the extension are not available here, only available in 'applyToCompilation'
        project.extensions.create(KotlinxReflectGradlePluginExtension.NAME, KotlinxReflectGradlePluginExtension::class.java)
        this.logger = project.logger

        val moduleSafeName = project.name.replace(Regex("[^a-zA-Z0-9]"), "_")

        val kotlinExtension = project.extensions.getByName("kotlin") as KotlinMultiplatformExtension
        val kotlinSourceSets = kotlinExtension.sourceSets

        kotlinSourceSets.forEach { ss ->
            when {
                (ss.name == KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME) -> {
                    val genDir = File(project.layout.buildDirectory.get().asFile, "kotlinxReflect/genSrc/${ss.name}")
                    genDir.mkdirs()
                    ss.kotlin.srcDir(genDir)
                    val moduleFile = File(genDir, "${KotlinxReflectRegisterForModuleClassName}.kt")
                    moduleFile.createNewFile()
                    val packageName = "${moduleSafeName}_${ss.name}"
                    this.kotlinxReflectRegisterForModuleClassFqNameMain = "$packageName.${KotlinxReflectRegisterForModuleClassName}"

                    moduleFile.printWriter().use { pw -> pw.println(KotlinxReflectRegisterForModuleTemplate(ss.name, packageName)) }

                }

                (ss.name == KotlinSourceSet.COMMON_TEST_SOURCE_SET_NAME) -> {
                    val genDir = File(project.layout.buildDirectory.get().asFile, "kotlinxReflect/genSrc/${ss.name}")
                    genDir.mkdirs()
                    ss.kotlin.srcDir(genDir)
                    val moduleFile = File(genDir, "${KotlinxReflectRegisterForModuleClassName}.kt")
                    moduleFile.createNewFile()
                    val packageName = "${moduleSafeName}_${ss.name}"
                    this.kotlinxReflectRegisterForModuleClassFqNameTest = "$packageName.${KotlinxReflectRegisterForModuleClassName}"
                    moduleFile.printWriter().use { pw -> pw.println(KotlinxReflectRegisterForModuleTemplate(ss.name, packageName)) }
                }

                else -> Unit
            }
        }
    }


    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension: KotlinxReflectGradlePluginExtension = project.extensions.getByType(KotlinxReflectGradlePluginExtension::class.java)

        // TODO: ensure kotlinx-reflect lib is a dependency
        //TODO: remove kotlin stdlib stuff
        this.forReflectionMainStr = extension.forReflectionMain.get().distinct().let {
            if (it.isNotEmpty())
                it.joinToString(java.io.File.pathSeparator) else
                null
        } ?: ""
        val total = extension.forReflectionMain.get() + extension.forReflectionTest.get()
        this.forReflectionTestStr = total.distinct().let {
            if (it.isNotEmpty())
                it.joinToString(java.io.File.pathSeparator) else
                null
        } ?: ""

        val classFqName = when (kotlinCompilation.compilationName) {
            KotlinCompilation.MAIN_COMPILATION_NAME -> this.kotlinxReflectRegisterForModuleClassFqNameMain
            KotlinCompilation.TEST_COMPILATION_NAME -> this.kotlinxReflectRegisterForModuleClassFqNameTest
            KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME -> this.kotlinxReflectRegisterForModuleClassFqNameMain
            KotlinSourceSet.COMMON_TEST_SOURCE_SET_NAME -> this.kotlinxReflectRegisterForModuleClassFqNameTest
            else -> error("Unhandled compilationName '${kotlinCompilation.compilationName}'")
        }
        val forReflection = when (kotlinCompilation.compilationName) {
            KotlinCompilation.MAIN_COMPILATION_NAME -> this.forReflectionMainStr
            KotlinCompilation.TEST_COMPILATION_NAME -> this.forReflectionTestStr
            KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME -> this.forReflectionMainStr
            KotlinSourceSet.COMMON_TEST_SOURCE_SET_NAME -> this.forReflectionTestStr
            else -> error("Unhandled compilationName '${kotlinCompilation.compilationName}'")
        }
        return project.provider {
            listOf(
                SubpluginOption(key = KotlinxReflectCommandLineProcessor.OPTION_kotlinxReflectRegisterForModuleClassFqName, value = classFqName),
                SubpluginOption(key = KotlinxReflectCommandLineProcessor.OPTION_forReflection, value = forReflection)
            )
        }
    }

}

open class KotlinxReflectGradlePluginExtension(objects: ObjectFactory) {

    companion object {
        val NAME = "kotlinxReflect"
    }

    /**
     * list of globs that define the classes for reflection
     */
    val forReflectionMain = objects.listProperty(String::class.java)

    /**
     * list of globs that define the classes for reflection
     * added to forReflectionMain for test modules
     */
    val forReflectionTest = objects.listProperty(String::class.java)

}

@AutoService(CommandLineProcessor::class)
class KotlinxReflectCommandLineProcessor : CommandLineProcessor {
    companion object {
        const val OPTION_forReflectionMain = "forReflection"
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

@AutoService(CompilerPluginRegistrar::class)
class KotlinxReflectComponentRegistrar(
    private val defaultReflectionLibs: String
) : CompilerPluginRegistrar() {

    @Suppress("unused") // Used by service loader
    constructor() : this(
        defaultReflectionLibs = ""
    )

    override val supportsK2: Boolean get() = TODO("not implemented")

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)

        messageCollector.report(CompilerMessageSeverity.LOGGING, "modularRoot = ${configuration.jvmModularRoots}")

        val forReflection = configuration.get(KotlinxReflectCommandLineProcessor.ARG_forReflection, defaultReflectionLibs)
            .split(java.io.File.pathSeparator).toList().filterNot { it.isNullOrBlank() }

        val kotlinxReflectRegisterForModuleClassFqName =
            configuration.get(KotlinxReflectCommandLineProcessor.ARG_kotlinxReflectRegisterForModuleClassFqName, defaultReflectionLibs)

        messageCollector.report(CompilerMessageSeverity.LOGGING, "configuration = ${configuration}")
        val dependencies = configuration.get(JSConfigurationKeys.LIBRARIES)
        if (null != dependencies) {
/*
            val allResolvedDependencies =  jsResolveLibraries(
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
 */
        }
        //JsSyntheticTranslateExtension.registerExtension(project, KotlinxReflectJsSyntheticTranslateExtension(messageCollector, forReflection))
        //AnalysisHandlerExtension.registerExtension(project, KotlinxReflectAnalysisHandlerExtension(messageCollector, forReflection))
        ExtraImportsProviderExtension.registerExtension(KotlinxReflectExtraImportsProviderExtension(messageCollector, forReflection))
        IrGenerationExtension.registerExtension(KotlinxReflectIrGenerationExtension(messageCollector, kotlinxReflectRegisterForModuleClassFqName, forReflection))

    }

}

