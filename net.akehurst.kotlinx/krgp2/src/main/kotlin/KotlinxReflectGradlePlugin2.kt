package net.akehurst.kotlinx.reflect.gradle.plugin

//import net.akehurst.kotlinx.text.toRegexFromGlob
import com.google.auto.service.AutoService
import net.akehurst.kotlinx.text.toRegexFromGlob
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.backend.common.reportLoadingProblemsIfAny
import org.jetbrains.kotlin.backend.common.serialization.metadata.DynamicTypeDeserializer
import org.jetbrains.kotlin.builtins.konan.KonanBuiltIns
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.library.metadata.KlibMetadataFactories
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.reflect.KClass
import org.jetbrains.kotlin.library.impl.*
import org.jetbrains.kotlin.library.*
import org.jetbrains.kotlin.library.loader.*
import org.jetbrains.kotlin.backend.common.loadMetadataKlibs
import org.jetbrains.kotlin.konan.file.*
import org.jetbrains.kotlin.library.metadata.parseModuleHeader

import org.jetbrains.kotlin.library.metadata.parsePackageFragment
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.addToStdlib.flatMapToNullable

typealias KFile = org.jetbrains.kotlin.konan.file.File

open class KotlinxReflectGradlePlugin2 : KotlinCompilerPluginSupportPlugin {

    companion object {
        const val KotlinxReflectRegisterForModuleClassName = "KotlinxReflectForModule"
        fun KotlinxReflectRegisterForModuleTemplate(moduleName: String, packageName: String, statements: String) = """  
            // ${moduleName}
            package $packageName
            import net.akehurst.kotlinx.reflect.KotlinxReflect
            import net.akehurst.kotlinx.reflect.EnumValuesFunction
            object $KotlinxReflectRegisterForModuleClassName {
              fun registerUsedClasses() {
              $statements
              }
              internal fun classForNameAfterRegistration(qualifiedName: String): kotlin.reflect.KClass<*> {
                this.registerUsedClasses()
                return net.akehurst.kotlinx.reflect.KotlinxReflect.classForName(qualifiedName = qualifiedName)
              }
            }
        """.trimIndent()

        private val KlibFactories = KlibMetadataFactories(::KonanBuiltIns, DynamicTypeDeserializer)
        val languageVersionSettings = LanguageVersionSettingsImpl(LanguageVersion.LATEST_STABLE, ApiVersion.LATEST_STABLE)

        class GradleToKotlin(val logger: Logger) : org.jetbrains.kotlin.util.Logger {
            override fun fatal(message: String): Nothing {
                error(message)
                throw Exception()
            }

            override fun error(message: String) = logger.error(message)
            override fun log(message: String) = logger.info(message)
            override fun warning(message: String) = logger.warn(message)
        }
    }

    private lateinit var logger: Logger

    private lateinit var kotlinxReflectRegisterForModuleClassFqNameMain: String
    private lateinit var kotlinxReflectRegisterForModuleClassFqNameTest: String
    private var forReflectionMainStr = ""
    private var forReflectionTestStr = ""

    override fun getCompilerPluginId(): String = KotlinxReflectPluginInfo.KOTLIN_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = KotlinxReflectPluginInfo.PROJECT_GROUP,
        artifactId = KotlinxReflectPluginInfo.PROJECT_NAME,
        version = KotlinxReflectPluginInfo.PROJECT_VERSION
    )

    override fun getPluginArtifactForNative(): SubpluginArtifact = SubpluginArtifact(
        groupId = KotlinxReflectPluginInfo.PROJECT_GROUP,
        artifactId = KotlinxReflectPluginInfo.PROJECT_NAME + "-native",
        version = KotlinxReflectPluginInfo.PROJECT_VERSION
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun apply(project: Project) {
        project.logger.info("KotlinxReflect: Applying KotlinxReflectGradlePlugin")
        //Note: the values assigned to the extension are not available here, only available in 'applyToCompilation'
        project.extensions.create(KotlinxReflectGradlePluginExtension.NAME, KotlinxReflectGradlePluginExtension::class.java)
        this.logger = project.logger

        project.afterEvaluate {
            val kotlinExtension = project.extensions.getByName("kotlin") as KotlinMultiplatformExtension
            val kotlinSourceSets = kotlinExtension.sourceSets

            kotlinSourceSets.getByName(KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME).dependencies {
                implementation("net.akehurst.kotlinx:kotlinx-reflect:${KotlinxReflectPluginInfo.PROJECT_VERSION}")
            }
        }
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val kotlinExtension = project.extensions.getByName("kotlin") as KotlinMultiplatformExtension
        val reflectExtension = project.extensions.getByType(KotlinxReflectGradlePluginExtension::class.java)
        val forReflectionMain = reflectExtension.forReflectionMain.get()
        val total = reflectExtension.forReflectionMain.get() + reflectExtension.forReflectionTest.get()
        val forReflectionTest = forReflectionMain + reflectExtension.forReflectionTest.get()
        val globRegexesMain = forReflectionMain.mapNotNull {
            if (it.isBlank()) {
                project.logger.warn("InternalError: Got and ignored null or blank class name!")
                null
            } else {
                Pair(it.toRegexFromGlob('.'), it)
            }
        }.associate {
            Pair(it.first, Pair(it.second, false))
        }.toMutableMap()
        val globRegexesTest = forReflectionMain.mapNotNull {
            if (it.isBlank()) {
                project.logger.warn( "InternalError: Got and ignored null or blank class name!")
                null
            } else {
                Pair(it.toRegexFromGlob('.'), it)
            }
        }.associate {
            Pair(it.first, Pair(it.second, false))
        }.toMutableMap()

        val moduleSafeName = project.name.replace(Regex("[^a-zA-Z0-9]"), "_")
        kotlinCompilation.kotlinSourceSets.forEach { ss ->
            when {
                (ss.name == KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME) -> {
                    val genDir = File(project.layout.buildDirectory.get().asFile, "kotlinxReflect/genSrc/${ss.name}")
                    genDir.mkdirs()
                    ss.kotlin.srcDir(genDir)
                    val moduleFile = File(genDir, "${KotlinxReflectRegisterForModuleClassName}.kt")
                    moduleFile.createNewFile()
                    val packageName = "${moduleSafeName}_${ss.name}"
                    this.kotlinxReflectRegisterForModuleClassFqNameMain = "$packageName.${KotlinxReflectRegisterForModuleClassName}"
                    val statements = generateStatements(project, globRegexesMain, kotlinCompilation)
                    moduleFile.printWriter().use { pw -> pw.println(KotlinxReflectRegisterForModuleTemplate(ss.name, packageName, statements)) }

                }

                (ss.name == KotlinSourceSet.COMMON_TEST_SOURCE_SET_NAME) -> {
                    val genDir = File(project.layout.buildDirectory.get().asFile, "kotlinxReflect/genSrc/${ss.name}")
                    genDir.mkdirs()
                    ss.kotlin.srcDir(genDir)
                    val moduleFile = File(genDir, "${KotlinxReflectRegisterForModuleClassName}.kt")
                    moduleFile.createNewFile()
                    val packageName = "${moduleSafeName}_${ss.name}"
                    this.kotlinxReflectRegisterForModuleClassFqNameTest = "$packageName.${KotlinxReflectRegisterForModuleClassName}"
                    val statements = generateStatements(project, globRegexesTest, kotlinCompilation)
                    moduleFile.printWriter().use { pw -> pw.println(KotlinxReflectRegisterForModuleTemplate(ss.name, packageName, statements)) }
                }

                else -> Unit
            }
        }
        return kotlinCompilation.target.project.provider { emptyList<SubpluginOption>() }
    }

    // this will handle dependencies, but not the current module being built
    private fun generateStatements(project: Project, globRegexes:MutableMap<Regex,Pair<String,Boolean>>, kotlinCompilation: KotlinCompilation<*>): String {
        val configurationName = kotlinCompilation.compileDependencyConfigurationName
        val regexes = globRegexes.keys

        val sb = StringBuilder()
        project.configurations.findByName(configurationName)?.let { cfg1 ->
            val c2 = cfg1.copy()
            project.afterEvaluate {
                val currentModuleMetadata = kotlinCompilation.output.allOutputs
                project.logger.error("currentModuleMetadata ${currentModuleMetadata.files}")
                val paths = c2.resolvedConfiguration.resolvedArtifacts.map { resArt -> resArt.file.path }
                val qualifiedNames = paths.flatMap { canonicalPath ->
                    val libraryFile = KFile(canonicalPath)
                    val component = "commonMain/default"
                    val zipAccessor = ZipFileSystemInPlaceAccessor
                    val metadataAccess = MetadataLibraryAccess<MetadataKotlinLibraryLayout>(libraryFile, component, zipAccessor)
                    val metadata = MetadataLibraryImpl(metadataAccess)
                    val mdh = parseModuleHeader(metadata.moduleHeaderData)
                    val qnames = mdh.packageFragmentNameList.flatMap { pkfFragName ->
                        metadata.packageMetadataParts(pkfFragName).flatMap { partName ->
                            val pf = parsePackageFragment(metadata.packageMetadata(pkfFragName, partName))
                            pf.class_List.map { pkfFragName+"."+pf.strings.getString(it.fqName) }
                        }
                    }
                    qnames
                }
                project.logger.error("trying to match $qualifiedNames")
                project.logger.error("against $regexes")
                val matchingNames = qualifiedNames.forEach{ qn ->
                    regexes.forEach { rg ->
                        if(rg.matches(qn)) {
                            sb.append("\"qn\"")
                        }
                    }
                }
            }
        }
        return sb.toString()
    }

}

// without the following 2 classes, the KotlinCompilerPluginSupportPlugin methods are not called
// even though they are not used.

@OptIn(ExperimentalCompilerApi::class)
@AutoService(CommandLineProcessor::class)
class KotlinxReflectCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = KotlinxReflectPluginInfo.KOTLIN_PLUGIN_ID

    override val pluginOptions: Collection<CliOption> = listOf(
    )
}

@OptIn(ExperimentalCompilerApi::class)
@AutoService(CompilerPluginRegistrar::class)
class KotlinxReflectComponentRegistrar(
    private val defaultReflectionLibs: String
) : CompilerPluginRegistrar() {
    override val supportsK2 = true
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {

    }
}