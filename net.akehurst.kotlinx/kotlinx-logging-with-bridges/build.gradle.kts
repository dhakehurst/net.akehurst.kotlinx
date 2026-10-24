
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":kotlinx-logging-common"))
                implementation(libs.kermit)
                implementation(libs.oshai.kotlin.logging)
            }
        }
        jvmMain {
            dependencies {
                runtimeOnly(libs.log4j.slf4j2.impl)
                runtimeOnly(libs.log4j.jcl)
                runtimeOnly(libs.log4j.jul)
                runtimeOnly(libs.log4j.v12.api)
                runtimeOnly(libs.jackson.dataformat.yaml)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.log4j.core)
            }
        }
    }
}