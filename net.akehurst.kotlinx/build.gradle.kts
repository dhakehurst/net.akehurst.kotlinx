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

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import com.github.gmazzo.gradle.plugins.BuildConfigExtension
import java.io.File

plugins {
    kotlin("multiplatform") version ("1.5.31") apply false
    id("net.akehurst.kotlin.gradle.plugin.exportPublic") version("1.3.0") apply false
    id("org.jetbrains.dokka") version ("1.4.32") apply false
    id("com.github.gmazzo.buildconfig") version("3.0.0") apply false
    id("nu.studer.credentials") version ("2.1")
}

allprojects {

    val version_project: String by project
    val group_project = rootProject.name

    group = group_project
    version = version_project

    buildDir = File(rootProject.projectDir, ".gradle-build/${project.name}")

}


fun getProjectProperty(s: String) = project.findProperty(s) as String?

subprojects {

    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "com.github.gmazzo.buildconfig")

    repositories {
        mavenLocal()
        mavenCentral()
    }

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
                    kotlinOptions {
                        jvmTarget = JavaVersion.VERSION_1_8.toString()
                    }
                }
                val test by compilations.getting {
                    kotlinOptions {
                        jvmTarget = JavaVersion.VERSION_1_8.toString()
                    }
                }
            }
            js("js", IR) {
                binaries.library()
                nodejs()
                browser()
            }
        }

        dependencies {
            "commonTestImplementation"(kotlin("test"))
            "commonTestImplementation"(kotlin("test-annotations-common"))
        }

        configure<PublishingExtension> {

        }
    }
}
