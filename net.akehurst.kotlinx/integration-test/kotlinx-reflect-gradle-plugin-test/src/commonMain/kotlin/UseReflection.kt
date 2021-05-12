package net.akehurst.kotlinx.reflect.gradle.plugin.test

import net.akehurst.kotlinx.reflect.ModuleRegistry
import net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.A
import net.akehurst.kotlinx.reflect.reflect
import kotlin.reflect.KClass

class UseReflection {

    fun reflect_construct_simpleName(): String {
        val cls = ModuleRegistry.classForName("net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.A")
        //val cls = KotlinxReflect.classForNameAfterRegistration("net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.A")
        val obj = cls.reflect().construct()

        return obj::class.simpleName ?: error("Cannot get simpleName")
    }


}

object KotlinxReflectEg {

    //private var _unregistered = true

    fun registerClasses() {
        //if (_unregistered) {
            ModuleRegistry.registerClass("net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.A", A::class)
        //    _unregistered = false
        //}
    }

    fun classForNameAfterRegistration(qualifiedName: String): KClass<*> {
        registerClasses()
        return ModuleRegistry.classForName(qualifiedName)
    }
}