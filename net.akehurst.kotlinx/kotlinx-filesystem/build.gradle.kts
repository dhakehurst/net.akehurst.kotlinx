kotlin {

    sourceSets {
        commonMain {
            dependencies {
               implementation(libs.kotlinx.coroutines.core)
            }
        }
        jsMain {
            dependencies {
                implementation("org.jetbrains.kotlin-wrappers:kotlin-js:2025.4.6")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-browser:2025.4.6")
            }
        }
        wasmJsMain {
            dependencies {
                implementation("org.jetbrains.kotlin-wrappers:kotlin-js:2025.4.6")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-browser:2025.4.6")
            }
        }
    }

}