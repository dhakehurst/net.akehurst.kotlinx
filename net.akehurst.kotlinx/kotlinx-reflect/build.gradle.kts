plugins {
    id("net.akehurst.kotlin.kt2ts") version "1.0.0"
}

dependencies {

    commonMainImplementation(project(":kotlinx-collections"))
    jvm8MainImplementation(kotlin("reflect"))
}


val tsdDir = "${buildDir}/tmp/jsJar/ts"

kotlin {
    sourceSets {
        val jsMain by getting {
            resources.srcDir("${tsdDir}")
        }
    }
}

kt2ts {
    outputDirectory.set(file(tsdDir))
    localJvmName.set("jvm8")
    modulesConfigurationName.set("jvm8RuntimeClasspath")
    classPatterns.set(listOf(
            "net.akehurst.kotlinx.reflect.*"
    ))
}

tasks.getByName("generateTypescriptDefinitionFile").dependsOn("jvm8MainClasses")
tasks.getByName("jsJar").dependsOn("generateTypescriptDefinitionFile")