package net.akehurst.kotlinx.reflect

import kotlin.reflect.KClass

expect class Reflection<T : Any>(clazz: KClass<T>) {

    val isAbstract: Boolean

    val allPropertyNames: List<String>

    fun createInstance() : T

    fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean

    fun callProperty(propertyName:String, obj:Any) : Any?

}

fun KClass<*>.reflect() = Reflection(this)
