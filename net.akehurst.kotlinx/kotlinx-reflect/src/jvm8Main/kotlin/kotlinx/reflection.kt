package net.akehurst.kotlinx.reflect

import net.akehurst.kotlinx.collections.transitveClosure
import kotlin.reflect.*
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.memberProperties


actual class Reflection<T : Any> actual constructor(val clazz: KClass<T>) {

    actual val isAbstract : Boolean = this.clazz.isAbstract

    actual val allPropertyNames:List<String> by lazy {
        this.clazz.memberProperties.map { it.name }
    }

    actual fun construct(vararg constructorArgs:Any?) : T {
        return this.clazz.constructors.first {
            //TODO: check types match
            it.parameters.size == constructorArgs.size
        }.call(*constructorArgs)
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

    actual fun allPropertyNames(obj:Any):List<String> {
        if (this.clazz.isInstance(obj)) {
            return this.allPropertyNames
        } else {
            throw RuntimeException("$obj is not an instance of ${this.clazz}")
        }
    }

    actual fun getProperty(propertyName:String, obj:Any) : Any? {
        val prop = obj::class.memberProperties.first { propertyName==it.name } as KProperty1<Any, *>
        return prop.get(obj)
    }
    actual fun setProperty(propertyName:String, obj:Any, value:Any?)  {
        val prop = obj::class.memberProperties.first { propertyName==it.name } as KMutableProperty1<Any, Any?>
        prop.set(obj, value)
    }
}

actual object ModuleRegistry {

    actual fun classForName(qualifiedName:String) :KClass<*> {
        //TODO: should we register for java also?
        return Class.forName(qualifiedName).kotlin
    }
}