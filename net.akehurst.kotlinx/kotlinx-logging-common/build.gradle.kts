
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
                api(libs.log4j.api) // api so that downstream libs get access
                implementation(libs.log4j.core) // used here, and on the runtime classpath of downstream libs
//                runtimeOnly(libs.log4j.slf4j2.impl)
//                runtimeOnly(libs.log4j.jcl)
//                runtimeOnly(libs.log4j.jul)
//                runtimeOnly(libs.log4j.v12.api)
//                runtimeOnly(libs.jackson.dataformat.yaml)
            }
        }
        webMain {
            dependencies {
                implementation(libs.kotlinx.wrappers.browser)
            }
        }
    }
}