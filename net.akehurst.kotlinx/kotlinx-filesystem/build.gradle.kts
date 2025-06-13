kotlin {

    sourceSets {
        commonMain {
            dependencies {
               implementation(libs.kotlinx.coroutines.core)
                // for VFS/resources filesystem
                implementation(libs.korlibs.korio)
            }
        }
        jsMain {
            dependencies {
                implementation(libs.kotlinx.wrappers.js)
                implementation(libs.kotlinx.wrappers.browser)
            }
        }
        wasmJsMain {
            dependencies {
                implementation(libs.kotlinx.wrappers.js)
                implementation(libs.kotlinx.wrappers.browser)
            }
        }
    }

}