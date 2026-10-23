plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    commonMainImplementation("net.akehurst.kotlinx:kotlinx-reflect:2.4.0-SNAPSHOT")
    commonMainImplementation(project(":kotlinx-reflect-gradle-plugin-test-moduleForReflection"))

    // Prototype: generate the registrations as source in commonMain, using KSP
    add("kspCommonMainMetadata", project(":kotlinx-reflect-ksp-processor"))
}

ksp {
    // classes from the dependency to register; the dependency itself applies no plugin
    arg("kotlinxReflect.forReflection", "net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.*")
    arg("kotlinxReflect.package", "net.akehurst.kotlinx.reflect.gradle.plugin.test.generated")
}

kotlin {
    js("js") {
        //binaries.library()
    }
    sourceSets.commonMain {
        kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/metadata/commonMain/kotlin"))
    }
}

// every compilation uses the code generated for commonMain
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
}
// the per-target KSP tasks also read commonMain sources, including the generated folder
tasks.matching { it.name.startsWith("ksp") && it.name != "kspCommonMainKotlinMetadata" }.configureEach {
    dependsOn("kspCommonMainKotlinMetadata")
}
