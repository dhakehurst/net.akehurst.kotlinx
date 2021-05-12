plugins {
    id("net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin") version("1.4.1")
}

dependencies {
    commonMainImplementation("net.akehurst.kotlinx:kotlinx-reflect:1.4.1")
    commonMainImplementation(project(":kotlinx-reflect-gradle-plugin-test-moduleForReflection"))
}