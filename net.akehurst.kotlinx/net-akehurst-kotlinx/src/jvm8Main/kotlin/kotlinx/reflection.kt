package net.akehurst.kotlinx.reflect

import net.akehurst.kotlinx.collections.transitveClosure
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createInstance


actual class Reflection<T : Any> actual constructor(val clazz: KClass<T>) {

    actual val isAbstract : Boolean = this.clazz.isAbstract

    actual fun createInstance() : T {
        return this.clazz.createInstance()
    }

    actual fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean {
        return this.clazz == subtype
                || subtype.supertypes.toSet().transitveClosure {
            val cls = it.classifier
            if (cls is KClass<*>) {
                cls.supertypes.toSet()
            } else {
                emptySet<KType>()
            }
        }.any { it.classifier == this.clazz }
    }

}

