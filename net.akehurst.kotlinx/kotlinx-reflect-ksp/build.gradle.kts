// KSP processor that generates kotlinx-reflect registrations as Kotlin source.
// Applied for users by the kotlinx-reflect Gradle plugin.
// Publishing is configured by the root build.

plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.ksp.api)
}
