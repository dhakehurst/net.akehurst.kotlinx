// This module plays the part of a third-party library:
// it applies no reflection or export plugins, and knows nothing about kotlinx-reflect.
// The module that depends on it (kotlinx-reflect-gradle-plugin-test) registers its classes for reflection.

kotlin {
    js {
        binaries.library()
    }
}
