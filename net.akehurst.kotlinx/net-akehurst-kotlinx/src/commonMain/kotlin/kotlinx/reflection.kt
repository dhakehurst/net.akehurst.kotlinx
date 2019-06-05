package net.akehurst.kotlinx.reflect

import kotlin.reflect.KClass

expect class Reflection<T : Any>(clazz: KClass<T>) {

    val isAbstract: Boolean

    fun createInstance() : T

    fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean

}

fun KClass<*>.reflect() = Reflection(this)
