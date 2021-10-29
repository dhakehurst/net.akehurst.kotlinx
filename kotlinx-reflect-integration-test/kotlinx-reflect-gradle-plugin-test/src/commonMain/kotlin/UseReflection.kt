package net.akehurst.kotlinx.reflect.gradle.plugin.test

import net.akehurst.kotlinx.reflect.KotlinxReflect
//import net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.A
import net.akehurst.kotlinx.reflect.reflect
import kotlin.js.JsExport

//import kotlin.reflect.KClass


class UseReflection {

    fun reflect_construct_simpleName(): String {
        val cls = KotlinxReflect.classForName("net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.AAAA")
        //val cls = KotlinxReflect.classForNameAfterRegistration("net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.AAAA")
        val obj = cls.reflect().construct()

        return obj::class.simpleName ?: error("Cannot get simpleName")
    }

    fun reflect_access_property(): Any {
        val cls = KotlinxReflect.classForName("net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.AAAA")
        //val cls = KotlinxReflect.classForNameAfterRegistration("net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.AAAA")
        val obj = cls.reflect().construct()

        return obj.reflect().getProperty("prop1") ?: error("Cannot get simpleName")
    }
}
/*
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
}*/