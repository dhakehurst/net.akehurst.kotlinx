package net.akehurst.kotlinx.reflect

import kotlin.reflect.KClass


actual class Reflection<T : Any> actual constructor(val clazz:KClass<T>) {

    actual val isAbstract:Boolean = TODO()

    actual fun createInstance() : T {
        TODO()
    }

    actual fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean{
        TODO()
    }

    actual fun callProperty(propertyName:String, obj:Any) : Any? {
        TODO()
    }
}

