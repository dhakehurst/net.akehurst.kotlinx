package net.akehurst.kotlinx.reflect.gradle.plugin

import org.gradle.api.model.ObjectFactory

open class KotlinxReflectGradlePluginExtension(objects: ObjectFactory) {

    companion object {
        val NAME = "kotlinxReflect"
    }

    /**
     * list of globs that define the classes for reflection
     */
    val forReflectionMain = objects.listProperty(String::class.java)

    /**
     * list of globs that define the classes for reflection
     * added to forReflectionMain for test modules
     */
    val forReflectionTest = objects.listProperty(String::class.java)

}