@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":kotlinx-logging-api"))
//                implementation(libs.kotlinx.html)
            }
        }
        wasmJsMain {
            dependencies {
                implementation(libs.kotlinx.wrappers.browser)
            }
        }
    }
}