
dependencies {
    jvmMainImplementation(kotlin("reflect"))
}

tasks.withType<AbstractPublishToMaven> {
    onlyIf { false }
}