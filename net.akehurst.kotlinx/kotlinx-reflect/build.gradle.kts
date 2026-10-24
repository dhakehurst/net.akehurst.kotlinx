
dependencies {

    commonMainImplementation(project(":kotlinx-collections"))
    jvmMainImplementation(kotlin("reflect"))

    jvmMainImplementation("org.jetbrains.kotlin:kotlin-metadata-jvm:${libs.versions.kotlin.get()}")
}


// Runs the JVM tests again with the registry backend, so JVM behaves exactly like JS, Wasm and Native
val jvmTestRegistry by tasks.registering(Test::class) {
    description = "Runs JVM tests using only the KotlinxReflect registry (kotlinx.reflect.forceRegistry=true)"
    group = "verification"
    val jvmTest = tasks.named<Test>("jvmTest").get()
    testClassesDirs = jvmTest.testClassesDirs
    classpath = jvmTest.classpath
    useJUnitPlatform()
    systemProperty("kotlinx.reflect.forceRegistry", "true")
}
tasks.named("allTests") { dependsOn(jvmTestRegistry) }
