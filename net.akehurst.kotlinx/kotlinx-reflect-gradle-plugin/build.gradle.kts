import com.github.gmazzo.buildconfig.BuildConfigExtension
import org.jetbrains.kotlin.gradle.tasks.UsesKotlinJavaToolchain

group = "net.akehurst.kotlin"

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.2.1"
    kotlin("jvm")
    kotlin("kapt") // does not respect buildDir, fixed in kotlin 1.5.20
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

val service = project.extensions.getByType<JavaToolchainService>()
val customLauncher = service.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(8))
}
project.tasks.withType<UsesKotlinJavaToolchain>().configureEach {
    kotlinJavaToolchain.toolchain.use(customLauncher)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

gradlePlugin {
    website.set("https://github.com/dhakehurst/net.akehurst.kotlinx")
    vcsUrl.set("https://github.com/dhakehurst/net.akehurst.kotlinx")
    plugins {
        create("kotlinx-reflect") {
            id = "net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin"
            implementationClass = "net.akehurst.kotlinx.reflect.gradle.plugin.KotlinxReflectGradlePlugin"
            displayName = project.name
            description = "Kotlin compiler plugin to support net.akehurst.kotlinx reflection on multi-platform"
            tags.set(listOf("reflection","kotlin", "javascript", "typescript", "kotlin-js", "kotlin-multiplatform"))
        }
    }
}

configure<BuildConfigExtension>  {
    //val project = project(":kotlinx-gradle-plugin")
    packageName("${project.group}.reflect.gradle.plugin")
    className("KotlinPluginInfo")
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin\"")
    buildConfigField("String", "PROJECT_GROUP", "\"${project.group}\"")
    buildConfigField("String", "PROJECT_NAME", "\"${project.name}\"")
    buildConfigField("String", "PROJECT_VERSION", "\"${project.version}\"")
}

dependencies {
    compileOnly(kotlin("gradle-plugin"))
    compileOnly("com.google.auto.service:auto-service:1.1.1")
    kapt("com.google.auto.service:auto-service:1.1.1")

    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    implementation(kotlin("gradle-plugin-api"))
    implementation(project(":kotlinx-reflect"))
    implementation(project(":kotlinx-utils"))
    implementation(kotlin("util-klib"))

    testImplementation(project)
    testImplementation(project(":kotlinx-reflect"))
    testImplementation(project(":kotlinx-utils"))

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-annotations-common"))
}

