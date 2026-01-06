@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

val version_logj4j = "2.25.1"
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
                api("org.apache.logging.log4j:log4j-core:$version_logj4j") // api so that downstream libs get access
                runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:$version_logj4j")
                runtimeOnly("org.apache.logging.log4j:log4j-jcl:$version_logj4j")
                runtimeOnly("org.apache.logging.log4j:log4j-jul:$version_logj4j")
                runtimeOnly("org.apache.logging.log4j:log4j-1.2-api:${version_logj4j}")
                runtimeOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.0")
            }
        }
        wasmJsMain {
            dependencies {
                implementation(libs.kotlinx.wrappers.browser)
            }
        }
    }
}