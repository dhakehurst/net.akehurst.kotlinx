
dependencies {

    commonMainImplementation(project(":kotlinx-collections"))
    jvmMainImplementation(kotlin("reflect"))

    jvmMainImplementation("org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlin.get()}")
}


exportPublic {
    exportPatterns.set(listOf(
        "net.akehurst.kotlinx.reflect.test.*",
    ))
}
