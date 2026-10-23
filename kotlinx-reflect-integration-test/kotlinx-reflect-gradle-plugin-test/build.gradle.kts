plugins {
    id("net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin") version ("2.4.0-SNAPSHOT")
}

dependencies {
    // the plugin adds net.akehurst.kotlinx:kotlinx-reflect itself
    commonMainImplementation(project(":kotlinx-reflect-gradle-plugin-test-moduleForReflection"))
}

kotlinxReflect {
    // classes from a dependency that applies no plugin (a "third-party" library), and this module's own classes
    forReflectionMain.set(listOf("net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.*", "net.akehurst.kotlinx.reflect.gradle.plugin.test.own.**"))
    // classes that only exist in commonTest
    forReflectionTest.set(listOf("net.akehurst.kotlinx.reflect.gradle.plugin.test.testonly.*"))
}

kotlin {
    js("js") {
        //binaries.library()
    }
}
