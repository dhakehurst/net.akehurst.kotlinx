plugins {
    id("net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin") version("1.4.1-1.6.0-RC")
}

dependencies {
    commonMainImplementation("net.akehurst.kotlinx:kotlinx-reflect:1.4.1")
    commonMainImplementation(project(":kotlinx-reflect-gradle-plugin-test-moduleForReflection"))
}

kotlinxReflect {
    forReflection.set(listOf("net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection"))
}

kotlin {
    js("js") {
        //binaries.library()

    }
}
/*
tasks.withType<org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile>().configureEach {
additionalExports.set(
    listOf(
        "net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.AAAA"
    )
)
}*/