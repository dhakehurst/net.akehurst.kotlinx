plugins {
    id("net.akehurst.kotlin.gradle.plugin.exportPublic") version("1.4.0-1.6.0-RC")
}
kotlin {
    js("js",IR) {
        binaries.library()
    }
}