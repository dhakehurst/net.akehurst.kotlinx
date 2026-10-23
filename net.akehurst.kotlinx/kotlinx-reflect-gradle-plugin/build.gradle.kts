import com.github.gmazzo.buildconfig.BuildConfigExtension
import org.jetbrains.kotlin.gradle.tasks.UsesKotlinJavaToolchain

group = "net.akehurst.kotlin"

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.2.1"
    kotlin("jvm")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val service = project.extensions.getByType<JavaToolchainService>()
val customLauncher = service.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(17))
}
project.tasks.withType<UsesKotlinJavaToolchain>().configureEach {
    kotlinJavaToolchain.toolchain.use(customLauncher)
}

gradlePlugin {
    website.set("https://github.com/dhakehurst/net.akehurst.kotlinx/tree/master/net.akehurst.kotlinx/kotlinx-reflect-gradle-plugin")
    vcsUrl.set("https://github.com/dhakehurst/net.akehurst.kotlinx")
    plugins {
        create("kotlinx-reflect") {
            id = "net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin"
            implementationClass = "net.akehurst.kotlinx.reflect.gradle.plugin.KotlinxReflectGradlePlugin"
            displayName = project.name
            description = "Generates registrations (using KSP) so that net.akehurst.kotlinx:kotlinx-reflect works on all Kotlin multiplatform targets, including for classes in third-party libraries"
            tags.set(listOf("reflection", "kotlin", "javascript", "wasm", "kotlin-js", "kotlin-multiplatform", "ksp"))
        }
    }
}

configure<BuildConfigExtension> {
    packageName("${project.group}.reflect.gradle.plugin")
    className("KotlinPluginInfo")
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin\"")
    buildConfigField("String", "PROJECT_GROUP", "\"${project.group}\"")
    buildConfigField("String", "PROJECT_NAME", "\"${project.name}\"")
    buildConfigField("String", "PROJECT_VERSION", "\"${project.version}\"")
    // group of the kotlinx-reflect runtime and KSP processor libraries
    buildConfigField("String", "LIBRARY_GROUP", "\"${rootProject.group}\"")
}

dependencies {
    // provided by the user's build
    compileOnly(kotlin("gradle-plugin"))
    // applied by this plugin
    implementation(libs.ksp.gradlePlugin)
}
