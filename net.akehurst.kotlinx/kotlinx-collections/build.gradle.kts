plugins {
    id("net.akehurst.kotlin.kt2ts") version "1.2.0"
}

dependencies {
        
}

kt2ts {
    jvmTargetName.set("jvm8")
    classPatterns.set(listOf(
            "net.akehurst.kotlinx.collections.*"
    ))
}
