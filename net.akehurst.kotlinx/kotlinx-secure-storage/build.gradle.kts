

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(project(":kotlinx-filesystem-api"))

                implementation(libs.cryptography.core)
                implementation(libs.cryptography.provider.optimal)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":kotlinx-filesystem"))
                implementation(project(":kotlinx-utils"))
            }
        }
    }
}