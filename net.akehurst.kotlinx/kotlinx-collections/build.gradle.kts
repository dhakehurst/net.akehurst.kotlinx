plugins {
    id("net.akehurst.kotlin.kt2ts") version "1.1.0"
}

dependencies {
        
}

kt2ts {
    localJvmName.set("jvm8")
    classPatterns.set(listOf(
            "net.akehurst.kotlinx.collections.*"
    ))
}
