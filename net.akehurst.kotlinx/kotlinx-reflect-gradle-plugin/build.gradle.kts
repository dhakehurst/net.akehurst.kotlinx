plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.14.0"
    kotlin("jvm")
    kotlin("kapt") // does not respect buildDir, fixed in kotlin 1.5.20
}
import com.github.gmazzo.gradle.plugins.BuildConfigExtension

configure<BuildConfigExtension>  {
    //val project = project(":kotlinx-gradle-plugin")
    packageName("${project.group}.reflect.gradle.plugin")
    className("KotlinPluginInfo")
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin\"")
    buildConfigField("String", "PROJECT_GROUP", "\"${project.group}\"")
    buildConfigField("String", "PROJECT_NAME", "\"${project.name}\"")
    buildConfigField("String", "PROJECT_VERSION", "\"${project.version}\"")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}



gradlePlugin {
    plugins {
        create("kotlinx-reflect") {
            id = "net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin"
            implementationClass = "net.akehurst.kotlinx.reflect.gradle.plugin.KotlinxReflectGradlePlugin"
        }
    }
}


dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    implementation(kotlin("gradle-plugin-api"))
    compileOnly("com.google.auto.service:auto-service:1.0-rc7")
    kapt("com.google.auto.service:auto-service:1.0-rc7")
    implementation(project(":kotlinx-reflect"))
}