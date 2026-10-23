// Prototype: a KSP processor that generates kotlinx-reflect registrations as Kotlin source.
// It can register classes from dependencies, so the library being reflected over needs no plugin.

plugins {
    kotlin("jvm")
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.3.12")
}
