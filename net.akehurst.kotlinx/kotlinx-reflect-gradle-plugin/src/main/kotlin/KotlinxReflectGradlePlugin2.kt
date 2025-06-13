package net.akehurst.kotlinx.reflect.gradle.plugin

import net.akehurst.kotlin.reflect.gradle.plugin.KotlinPluginInfo
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.analyzer.CompilationErrorException
import org.jetbrains.kotlin.backend.common.CommonKLibResolver
import org.jetbrains.kotlin.backend.common.serialization.metadata.DynamicTypeDeserializer
import org.jetbrains.kotlin.backend.common.toLogger
import org.jetbrains.kotlin.builtins.konan.KonanBuiltIns
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMetadataCompilation
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.konan.file.ZipFileSystemInPlaceAccessor
import org.jetbrains.kotlin.library.metadata.KlibMetadataFactories
import org.jetbrains.kotlin.library.nativeTargets
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import java.io.File
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.reflect.KClass

class KotlinxReflectGradlePlugin2 : KotlinCompilerPluginSupportPlugin {

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
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        project.logger.info("KotlinxReflect: ${kotlinCompilation.name}")
        project.logger.info("KotlinxReflect: ${kotlinCompilation.target.platformType.name}")
        project.logger.info("KotlinxReflect: ${kotlinCompilation.kotlinSourceSets}")
        project.logger.info("KotlinxReflect: ${kotlinCompilation.compileDependencyConfigurationName}")

        val kotlinExtension = project.extensions.getByName("kotlin") as KotlinMultiplatformExtension

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
                    val statements = generateStatements(project, kotlinCompilation.compileDependencyConfigurationName)
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
                    val statements = generateStatements(project, kotlinCompilation.compileDependencyConfigurationName)
                    moduleFile.printWriter().use { pw -> pw.println(KotlinxReflectRegisterForModuleTemplate(ss.name, packageName, statements)) }
                }

                else -> Unit
            }
        }


        return kotlinCompilation.target.project.provider { emptyList<SubpluginOption>() }
    }

    private fun generateStatements(project: Project, configurationName: String): String {
        project.logger.info("KotlinxReflect: $configurationName")
        val sb = StringBuilder()
        project.configurations.findByName(configurationName)?.let { cfg1 ->
            val c2 = cfg1.copy()
            project.afterEvaluate {

                val dependencies = cfg1.dependencies.map {
                    project.logger.info("dep '${it.name}'")
                    it.name
                }.filter { Files.exists(Path(it)) }
                val res = CommonKLibResolver.resolveWithoutDependencies(
                    dependencies,
                    GradleToKotlin(logger),
                    ZipFileSystemInPlaceAccessor,
                    duplicatedUniqueNameStrategy = DuplicatedUniqueNameStrategy.DENY
                )
                val storageManager = LockBasedStorageManager("klib")
                project.logger.info("Modules: ${res.libraries.size}")
                res.resolveWithDependencies().forEach { lib, pa ->
                    project.logger.info("module '${lib.libraryName}'")
                    val module = KlibFactories.DefaultDeserializedDescriptorFactory.createDescriptorAndNewBuiltIns(lib, languageVersionSettings, storageManager, null)
                    project.logger.info("module '${module.name}'")
                }
            }
        }
        return sb.toString()
    }

    private fun findClasses(packageName: String): List<KClass<*>> {
        val result = mutableListOf<KClass<*>>()
        val path = "/" + packageName.replace('.', '/')
        val uri = this::class.java.getResource(path)!!.toURI()
        var fileSystem: FileSystem? = null
        val filePath = if (uri.scheme == "jar") {
            fileSystem = FileSystems.newFileSystem(uri, emptyMap<String, Any>())
            fileSystem.getPath(path)
        } else {
            Paths.get(uri)
        }
        val stream = Files.walk(filePath, 1)
            .filter { f -> !f.name.contains('$') && f.name.endsWith(".class") }
        for (file in stream) {
            val cn = file.name.dropLast(6) // remove .class
            val qn = "${packageName}.$cn"
            val kclass = Class.forName(qn).kotlin
            result.add(kclass)
        }
        fileSystem?.close()
        return result
    }
}

