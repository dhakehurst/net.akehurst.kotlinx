
dependencies {

    "commonMainImplementation"(project(":kotlinx-collections"))
    "jvm8MainImplementation"(kotlin("reflect"))
}


exportPublic {
    exportPatterns.set(listOf(
        "net.akehurst.kotlinx.reflect.ClassReflection",
    ))
}