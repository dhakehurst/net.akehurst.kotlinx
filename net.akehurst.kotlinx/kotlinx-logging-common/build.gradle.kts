@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

val version_loj4j = "2.25.1"
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":kotlinx-logging-api"))
//                implementation(libs.kotlinx.html)
            }
        }
        jvmMain {
            dependencies {
                implementation("org.apache.logging.log4j:log4j-core:$version_loj4j")
                implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$version_loj4j")
                implementation("org.apache.logging.log4j:log4j-jcl:2.25.1")
                runtimeOnly("org.apache.logging.log4j:log4j-jul:2.25.1")
            }
        }
        wasmJsMain {
            dependencies {
                implementation(libs.kotlinx.wrappers.browser)
            }
        }
    }
}