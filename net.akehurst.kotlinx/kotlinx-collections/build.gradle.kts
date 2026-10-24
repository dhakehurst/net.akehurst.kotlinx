
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // api: Reference (from kotlinx-utils) is part of this module's public API (ManagedReference*)
                api(project(":kotlinx-utils"))
            }
        }
    }

}