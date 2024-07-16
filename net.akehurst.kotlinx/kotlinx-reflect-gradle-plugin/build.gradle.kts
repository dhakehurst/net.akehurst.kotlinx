import com.github.gmazzo.gradle.plugins.BuildConfigExtension

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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

val testPlugin by sourceSets.creating{}

gradlePlugin {
    website.set("https://github.com/dhakehurst/net.akehurst.kotlinx")
    vcsUrl.set("https://github.com/dhakehurst/net.akehurst.kotlinx")
    plugins {
        create("kotlinx-reflect") {
            id = "net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin"
            implementationClass = "net.akehurst.kotlinx.reflect.gradle.plugin.KotlinxReflectGradlePlugin"
            description = "Kotlin compiler plugin to support net.akehurst.kotlinx reflection on JS-platform"
            tags.set(listOf("reflection","kotlin", "javascript", "typescript", "kotlin-js", "kotlin-multiplatform"))

        }
    }
    testSourceSets(testPlugin)
}

val version_junit:String by project
dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    compileOnly(kotlin("gradle-plugin"))
    compileOnly("com.google.auto.service:auto-service:1.1.1")
    kapt("com.google.auto.service:auto-service:1.1.1")

    implementation(kotlin("gradle-plugin-api"))
    implementation(project(":kotlinx-reflect"))

    // seem to need these to be able to run the pluginTest
    //implementation(kotlin("gradle-plugin"))
    //implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")

    "testPluginImplementation"(project)
    "testPluginImplementation"(gradleTestKit())
    "testPluginImplementation"("org.junit.jupiter:junit-jupiter:$version_junit")
    "testPluginImplementation"(kotlin("gradle-plugin"))
    "testPluginImplementation"("org.jetbrains.kotlin:kotlin-compiler-embeddable")

}
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
val testPluginTask = tasks.register<Test>("testPlugin") {
    description = "Runs the plugin tests."
    group = "verification"
    testClassesDirs = testPlugin.output.classesDirs
    classpath = testPlugin.runtimeClasspath
    mustRunAfter(tasks.test)
}

