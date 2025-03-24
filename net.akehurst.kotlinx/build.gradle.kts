/**
 * Copyright (C) 2019 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.github.gmazzo.buildconfig.BuildConfigExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File

plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.buildconfig) apply false
    alias(libs.plugins.credentials) apply true
    alias(libs.plugins.exportPublic) apply false
}
val kotlin_languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1
val kotlin_apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1
val jvmTargetVersion = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8

allprojects {

    repositories {
        mavenLocal {
            content{
                includeGroupByRegex("net\\.akehurst.+")
            }
        }
        mavenCentral()
    }

    group = rootProject.name
    version = rootProject.libs.versions.project.get()

    project.layout.buildDirectory = File(rootProject.projectDir, ".gradle-build/${project.name}")

}


fun getProjectProperty(s: String) = project.findProperty(s) as String?

subprojects {

    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "com.github.gmazzo.buildconfig")

    if (name!="kotlinx-reflect-gradle-plugin") {
        apply(plugin = "org.jetbrains.kotlin.multiplatform")
        apply(plugin = "net.akehurst.kotlin.gradle.plugin.exportPublic")

        configure<BuildConfigExtension> {
            val now = java.time.Instant.now()
            fun fBbuildStamp(): String = java.time.format.DateTimeFormatter.ISO_DATE_TIME.withZone(java.time.ZoneId.of("UTC")).format(now)
            fun fBuildDate(): String = java.time.format.DateTimeFormatter.ofPattern("yyyy-MMM-dd").withZone(java.time.ZoneId.of("UTC")).format(now)
            fun fBuildTime(): String= java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss z").withZone(java.time.ZoneId.of("UTC")).format(now)

            buildConfigField("String", "version", "\"${project.version}\"")
            buildConfigField("String", "buildStamp", "\"${fBbuildStamp()}\"")
            buildConfigField("String", "buildDate", "\"${fBuildDate()}\"")
            buildConfigField("String", "buildTime", "\"${fBuildTime()}\"")
        }

        configure<KotlinMultiplatformExtension> {
            jvm("jvm8") {
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
            js("js", IR) {
                binaries.library()
                nodejs()
                browser()
                compilerOptions {
                    target.set("es2015")
                }
            }

            @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
            wasmJs() {
                binaries.library()
                browser()
            }

            macosArm64()

            applyDefaultHierarchyTemplate()
        }

        dependencies {
            "commonTestImplementation"(kotlin("test"))
            "commonTestImplementation"(kotlin("test-annotations-common"))
        }

    }

    configure<SigningExtension> {
        useGpgCmd()
        val publishing = project.properties["publishing"] as PublishingExtension
        sign(publishing.publications)
    }

    val creds = project.properties["credentials"] as nu.studer.gradle.credentials.domain.CredentialsContainer
    configure<PublishingExtension> {
        repositories {
            maven {
                name = "Other"
                setUrl(getProjectProperty("PUB_URL")?: "<use -P PUB_URL=<...> to set>")
                credentials {
                    username = getProjectProperty("PUB_USERNAME")
                        ?: error("Must set project property with Username (-P PUB_USERNAME=<...> or set in ~/.gradle/gradle.properties)")
                    password = getProjectProperty("PUB_PASSWORD")?: creds.forKey(getProjectProperty("PUB_USERNAME"))
                }
            }
        }

    }
}
