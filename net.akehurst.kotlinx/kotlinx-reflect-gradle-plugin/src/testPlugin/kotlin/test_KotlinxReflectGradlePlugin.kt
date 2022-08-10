package net.akehurst.kotlinx.reflect.gradle.plugin

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence


class test_KotlinxReflectGradlePlugin {

    //@TempDir
    var testProjectDir: File? = Files.createTempDirectory("testTemp").toFile()
    private var settingsFile = File(testProjectDir, "settings.gradle.kts")
    private var gradlePropertiesFile = File(testProjectDir, "gradle.properties")
    private var buildFile = File(testProjectDir, "build.gradle.kts")
    private val codeFile_AAA = File(testProjectDir, "src/main/kotlin/test/AAA.kt")


    @AfterEach
    fun after() {
        Files.walk(testProjectDir?.toPath()).use { dirStream ->
            dirStream.asSequence()
                .map(Path::toFile)
                .sortedWith(Comparator.reverseOrder())
                .forEach { obj -> obj.delete() }
        }
    }

    @Test
    fun testCompilerPlugin() {
        println("$testProjectDir ${testProjectDir?.exists()}")

        //Given
        settingsFile.writeText(
            """
                rootProject.name = "test-plugin"
            """.trimIndent()
        )
        gradlePropertiesFile.writeText(
            """
                kotlin.mpp.stability.nowarn=true
            """.trimIndent()
        )
        buildFile.writeText(
            """
                plugins {
                    kotlin("multiplatform") version ("1.7.20-Beta")
                    id("net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin")
                }
                val kotlin_languageVersion = "1.7"
                val kotlin_apiVersion:String = "1.7"
                val jvmTargetVersion = JavaVersion.VERSION_1_8.toString()
                //kotlinxReflect {
               //     forReflection.set(listOf(
                //        "test",
               //     ))
               // }
               repositories {
                   mavenLocal {
                       content{
                           includeGroupByRegex("net\\.akehurst.+")
                       }
                   }
                   mavenCentral()
               }
               
               kotlin {
                   jvm {
                       val main by compilations.getting {
                           kotlinOptions {
                               languageVersion = kotlin_languageVersion
                               apiVersion = kotlin_apiVersion
                               jvmTarget = jvmTargetVersion
                           }
                       }
                       val test by compilations.getting {
                           kotlinOptions {
                               languageVersion = kotlin_languageVersion
                               apiVersion = kotlin_apiVersion
                               jvmTarget = jvmTargetVersion
                           }
                       }
                   }
                   js(IR) {
                       binaries.library()
                       nodejs()
                       browser()
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
            .withArguments("compileKotlinJvm","--debug")
            .withPluginClasspath()
            .withDebug(true)
            .forwardStdError(System.err.writer())
            .forwardStdOutput(System.out.writer())
            .build()

        //Then
        //println(result.output)
    }

}