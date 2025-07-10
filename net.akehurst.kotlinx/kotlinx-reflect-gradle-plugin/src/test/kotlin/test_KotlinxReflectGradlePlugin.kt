package net.akehurst.kotlinx.reflect.gradle.plugin

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class test_KotlinxReflectGradlePlugin {

    var testProjectDir: File? = Files.createTempDirectory("testTemp").toFile()
//    var testProjectDir: File? = Files.createDirectories(Paths.get("/Users/akehurst/temp/kotlinTest")).toFile()
    private var settingsFile = File(testProjectDir, "settings.gradle.kts")
    private var gradlePropertiesFile = File(testProjectDir, "gradle.properties")
    private var buildFile = File(testProjectDir, "build.gradle.kts")
    private val codeFile_AAA = File(testProjectDir, "src/commonMain/kotlin/test/AAA.kt")


    @AfterTest
    fun after() {
/*        Files.walk(testProjectDir?.toPath()).use { dirStream ->
            dirStream.asSequence()
                .map(Path::toFile)
                .sortedWith(Comparator.reverseOrder())
                .forEach { obj -> obj.delete() }
        }*/
    }

    @Test
    fun testCompilerPlugin() {
        println("Test dir: $testProjectDir ${testProjectDir?.exists()}")

        //Given
        settingsFile.writeText(
            """
                rootProject.name = "test-plugin"
            """.trimIndent()
        )
        gradlePropertiesFile.writeText(
            """
                //kotlin.mpp.stability.nowarn=true
            """.trimIndent()
        )
        buildFile.writeText(
            """
                plugins {
                    kotlin("multiplatform") version ("2.2.0")
                    id("net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin") //version("2.2.0-SNAPSHOT") 
                }
                val kotlin_languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
                val kotlin_apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
                val jvmTargetVersion = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
                
                kotlinxReflect {
                    forReflectionMain.set(listOf(
                        "test.*",
                    ))
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
                    applyDefaultHierarchyTemplate()
                    jvm {
                        compilations {
                            val main by compilations.getting {
                                compileTaskProvider.configure {
                                    compilerOptions {
                                        languageVersion.set(kotlin_languageVersion)
                                        apiVersion.set(kotlin_apiVersion)
                                        jvmTarget.set(jvmTargetVersion)
                                    }
                                }
                            }
                            val test by compilations.getting {
                                compileTaskProvider.configure {
                                    compilerOptions {
                                        languageVersion.set(kotlin_languageVersion)
                                        apiVersion.set(kotlin_apiVersion)
                                        jvmTarget.set(jvmTargetVersion)
                                    }
                                }
                            }
                        }
                    }
                }
            """.trimIndent()
        )
        codeFile_AAA.parentFile.mkdirs()
        codeFile_AAA.writeText(
            """
                    package test
                    class AAA {
                        fun f(list:List<Int>) {
                            list.map(Int::inc)
                        }
                    }
                """.trimIndent()
        )

        //When
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("compileKotlin","--stacktrace")
            .withPluginClasspath()
            .withDebug(true)
            .forwardStdError(System.err.writer())
            .forwardStdOutput(System.out.writer())
            .build()

        //Then
        //println(result.output)
    }

}