package net.akehurst.kotlinx.reflect

import net.akehurst.kotlinx.collections.transitveClosure
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.memberProperties


actual class Reflection<T : Any> actual constructor(val clazz: KClass<T>) {

    actual val isAbstract : Boolean = this.clazz.isAbstract

    actual val allPropertyNames:List<String> by lazy {
        this.clazz.memberProperties.map { it.name }
    }

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

    actual fun callProperty(propertyName:String, obj:Any) : Any? {
        val prop = obj::class.memberProperties.first { propertyName==it.name } as KProperty1<Any, *>
        return prop.get(obj)
    }

}

