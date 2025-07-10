import com.github.gmazzo.buildconfig.BuildConfigExtension
import org.gradle.kotlin.dsl.configure

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.2.1"
    kotlin("jvm")
    kotlin("kapt") // does not respect buildDir, fixed in kotlin 1.5.20
}
val pluginId = "net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin"
/*
gradlePlugin {
    website.set("https://github.com/dhakehurst/net.akehurst.kotlinx")
    vcsUrl.set("https://github.com/dhakehurst/net.akehurst.kotlinx")
    plugins {
        create("kotlinx-reflect") {
            id = pluginId
            implementationClass = "net.akehurst.kotlinx.reflect.gradle.plugin.KotlinxReflectGradlePlugin2"
            displayName = project.name
            description = "Kotlin compiler plugin to support net.akehurst.kotlinx reflection on multi-platform"
            tags.set(listOf("reflection","kotlin", "javascript", "typescript", "kotlin-js", "kotlin-multiplatform"))
        }
    }
}
*/
configure<BuildConfigExtension>  {
    //val project = project(":kotlinx-gradle-plugin")
    packageName("${project.group}.reflect.gradle.plugin")
    className("KotlinxReflectPluginInfo")
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"$pluginId\"")
    buildConfigField("String", "PROJECT_GROUP", "\"${project.group}\"")
    buildConfigField("String", "PROJECT_NAME", "\"${project.name}\"")
    buildConfigField("String", "PROJECT_VERSION", "\"${project.version}\"")
}

dependencies {
    //compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    //compileOnly(kotlin("gradle-plugin"))
    compileOnly("com.google.auto.service:auto-service:1.1.1")
   // kapt("com.google.auto.service:auto-service:1.1.1")

    implementation(kotlin("gradle-plugin"))
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    implementation(project(":kotlinx-utils"))

    testImplementation(project)
    //testImplementation(project(":kotlinx-utils"))
    //testImplementation(gradleTestKit())
   // testImplementation(kotlin("gradle-plugin"))
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-annotations-common"))
}

