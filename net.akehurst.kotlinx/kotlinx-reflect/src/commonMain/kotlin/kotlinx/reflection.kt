package net.akehurst.kotlinx.reflect

import kotlin.reflect.KClass

expect class Reflection<T : Any>(clazz: KClass<T>) {

    val isAbstract: Boolean

    val allPropertyNames: List<String>

    fun construct(vararg constructorArgs: Any?): T

    fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean

    fun allPropertyNames(obj: Any): List<String>

    fun getProperty(propertyName: String, obj: Any): Any?

    fun setProperty(propertyName: String, obj: Any, value: Any?)
}

fun KClass<*>.reflect() = Reflection(this)

expect object ModuleRegistry {

    fun register(module:Any)

    fun classForName(qualifiedName:String) :KClass<*>

}