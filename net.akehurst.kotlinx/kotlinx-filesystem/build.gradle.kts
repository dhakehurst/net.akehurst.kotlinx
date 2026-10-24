plugins {
    alias(libs.plugins.jsPlainObjects)
}
kotlin {
    // browser tests (src/webTest) use the browser's private file system, so run them in headless Chrome
    js {
        browser {
            testTask { useKarma { useChromeHeadless() } }
        }
    }
    wasmJs {
        browser {
            testTask { useKarma { useChromeHeadless() } }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":kotlinx-filesystem-api"))
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

        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }

}