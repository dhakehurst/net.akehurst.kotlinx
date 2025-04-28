
dependencies {

    commonMainImplementation(project(":kotlinx-collections"))
    jvm8MainImplementation(kotlin("reflect"))

    jvm8MainImplementation("org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlin.get()}")
}


//exportPublic {
//    exportPatterns.set(listOf(
//        "net.akehurst.kotlinx.reflect.ClassReflection",
//    ))
//}