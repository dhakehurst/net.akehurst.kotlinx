plugins {
    id("net.akehurst.kotlin.kt2ts") version "1.1.0"
}

dependencies {

    commonMainImplementation(project(":kotlinx-collections"))
    jvm8MainImplementation(kotlin("reflect"))
}

kt2ts {
    localJvmName.set("jvm8")
    classPatterns.set(listOf(
            "net.akehurst.kotlinx.reflect.*"
    ))
}

