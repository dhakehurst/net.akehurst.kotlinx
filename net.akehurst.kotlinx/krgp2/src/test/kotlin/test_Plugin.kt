package net.akehurst.kotlinx.reflect.gradle.plugin

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class test_Plugin {

    // Get the plugin's classpath from the current build.
    // This assumes your test module directly depends on your plugin module.
    // In build.gradle.kts of your test module:
    // dependencies {
    //    implementation(project(":your-plugin-module"))
    // }
    private val pluginClasspath: List<File> by lazy {
        // This task needs to be executed to get the runtime classpath of the plugin.
        // It's specific to the Gradle test environment setup.
        // It's often provided by the 'java-gradle-plugin' or 'kotlin-gradle-plugin'.
        val classpathFile = File("build/plugin-classpath.txt") // Or similar path
        if (!classpathFile.exists()) {
            // You might need to run ':your-plugin-module:jar' or ':your-plugin-module:assemble'
            // before running tests to ensure the JAR exists.
            // Or better: ensure the build of your plugin module happens as part of test task.
            // For example, by depending on 'project(':your-plugin-module').tasks.named("jar")'
        }
        classpathFile.readLines().map { File(it) }
    }

    val testProjectDir = createTempDirectory("test_plugin")
    private val buildFile = testProjectDir.resolve("build.gradle.kts").toFile()
    private val settingsFile = testProjectDir.resolve("settings.gradle.kts").toFile()
    private val codeFile = testProjectDir.resolve("src/commonMain/kotlin/test/code.kt").toFile()

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun afterTest() {
       // testProjectDir.deleteRecursively()
    }

    @Test
    fun test() {
        println("Test generated in: $buildFile")
        val settingsFileContent = """
            pluginManagement {
                repositories {
                    mavenLocal {
                        content{
                            includeGroupByRegex("net\\.akehurst.+")
                        }
                    }
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
        """.trimIndent()
        settingsFile.createNewFile()
        settingsFile.writeText(settingsFileContent)

        val kv = "2.2.20"//KotlinVersion.CURRENT.toString()
        val buildFileContent = $$"""
println("===============================================")
println("Gradle: ${GradleVersion.current()}")
println("Kotlin: ${KotlinVersion.CURRENT}")
println("JVM: ${org.gradle.internal.jvm.Jvm.current()} '${org.gradle.internal.jvm.Jvm.current().javaHome}'")
println("===============================================")            
            
            plugins {
              kotlin("multiplatform") version ("$$kv")
              id("net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin") version("2.2.20-SNAPSHOT")
            }
            repositories {
                mavenLocal {
                    content{
                        includeGroupByRegex("net\\.akehurst.+")
                    }
                }            
                mavenCentral()
            }
            kotlin {
                //applyDefaultHierarchyTemplate()
                jvm()
                //js {
                //    browser()
                //}
            }
            kotlinxReflect {
                forReflectionMain.set(listOf(
                    "test",
                ))
            }
            """.trimIndent()
        buildFile.createNewFile()
        buildFile.writeText(buildFileContent)

        val codeFileContent = """
            package test
            class AAA {
                fun f(list:List<Int>) {
                    list.map(Int::inc)
                }
            }            
        """.trimIndent()
        codeFile.parentFile.mkdirs()
        codeFile.createNewFile()
        codeFile.writeText(codeFileContent)

        val task = "build"
        val result: BuildResult = GradleRunner.create()
            .withGradleVersion("8.14.2")
            .withPluginClasspath()
            .withProjectDir(testProjectDir.toFile())
            .withArguments(
                //"-Dorg.gradle.daemon=false",
                //"-Pkotlin.compiler.execution.strategy=in-process",
                "--info",
                "--stacktrace",
                task
            )
            .forwardStdError(System.err.writer())
            .forwardStdOutput(System.out.writer())
            //.withDebug(true)
            .build()

        assertEquals(result.task(":$task")?.outcome, TaskOutcome.SUCCESS)
    }

}