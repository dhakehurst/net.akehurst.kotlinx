plugins {
    id("net.akehurst.kotlin.gradle.plugin.exportPublic") version("2.2.21")
    id("net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin") version ("2.2.21-SNAPSHOT")
}
kotlin {
    js {
        binaries.library()
    }
}

// TODO: make it so this appears on the module that uses it, not the module that provides it
// used to work in older version, but kotlin changed, and currently not figured out how to do it.
// there is work in progress to try
kotlinxReflect {
    forReflectionMain.set(
        listOf(
            "net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.*"
        )
    )
}