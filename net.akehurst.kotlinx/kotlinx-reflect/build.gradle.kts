plugins {
    id("net.akehurst.kotlin.kt2ts") version "1.2.0"
}

dependencies {

    commonMainImplementation(project(":kotlinx-collections"))
    jvm8MainImplementation(kotlin("reflect"))
}

kt2ts {
    jvmTargetName.set("jvm8")
    classPatterns.set(listOf(
            "net.akehurst.kotlinx.reflect.*"
    ))
}

