plugins {
 //   id("net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin") version ("2.2.21-SNAPSHOT")
}

dependencies {
    commonMainImplementation("net.akehurst.kotlinx:kotlinx-reflect:2.2.21")
    commonMainImplementation(project(":kotlinx-reflect-gradle-plugin-test-moduleForReflection"))
}
/*
kotlinxReflect {
    forReflectionMain.set(
        listOf(
            "net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.*"
        )
    )
}
*/
kotlin {
    js("js") {
        //binaries.library()
    }
}
