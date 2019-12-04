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

import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.tasks.BintrayUploadTask
import org.gradle.api.publish.maven.internal.artifact.FileBasedMavenArtifact
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File

plugins {
    kotlin("multiplatform") version("1.3.60") apply false
    id("net.akehurst.kotlin.kt2ts") version("1.3.0") apply false
    id("com.jfrog.bintray") version("1.8.4") apply false
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
    apply(plugin="org.jetbrains.kotlin.multiplatform")
    apply(plugin="net.akehurst.kotlin.kt2ts")
    apply(plugin = "maven-publish")
    apply(plugin = "com.jfrog.bintray")

    repositories {
        mavenCentral()
        jcenter()
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
        js("js") {
            nodejs()
            browser()
        }
    }

    dependencies {
        "commonMainImplementation"(kotlin("stdlib"))
        "commonTestImplementation"(kotlin("test"))
        "commonTestImplementation"(kotlin("test-annotations-common"))

        "jvm8MainImplementation"(kotlin("stdlib-jdk8"))
        "jvm8TestImplementation"(kotlin("test-junit"))

        "jsMainImplementation"(kotlin("stdlib-js"))
        "jsTestImplementation"(kotlin("test-js"))
    }

    configure<PublishingExtension> {

    }

    configure<BintrayExtension> {
        user = getProjectProperty("bintrayUser")
        key = getProjectProperty("bintrayApiKey")
        publish = true
        override = true
        setPublications("kotlinMultiplatform","metadata","js","jvm8")
        pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
            repo = "maven"
            name = "${rootProject.name}"
            userOrg = user
            websiteUrl = "https://github.com/dhakehurst/net.akehurst.kotlinx"
            vcsUrl = "https://github.com/dhakehurst/net.akehurst.kotlinx"
            setLabels("kotlin")
            setLicenses("Apache-2.0")
        })
    }

    val bintrayUpload by tasks.getting
    val publishToMavenLocal by tasks.getting
    val publishing = extensions.getByType(PublishingExtension::class.java)
    
    bintrayUpload.dependsOn(publishToMavenLocal)

    tasks.withType<BintrayUploadTask> {
        doFirst {
            publishing.publications
                    .filterIsInstance<MavenPublication>()
                    .forEach { publication ->
                        val moduleFile = buildDir.resolve("publications/${publication.name}/module.json")
                        if (moduleFile.exists()) {
                            publication.artifact(object : FileBasedMavenArtifact(moduleFile) {
                                override fun getDefaultExtension() = "module"
                            })
                        }
                    }
        }
    }
}
