package net.akehurst.kotlinx.reflect

import net.akehurst.kotlinx.collections.transitveClosure
import kotlin.reflect.*
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.isAccessible


actual class Reflection<T : Any> actual constructor(val clazz: KClass<T>) {

    actual val isAbstract: Boolean = this.clazz.isAbstract

    actual val allPropertyNames: List<String> by lazy {
        //find get methods, for java defined classes/properties
        val methProps = this.clazz.memberFunctions.filter { it.name.startsWith("get") && it.valueParameters.size == 0 }.map { it.name.substring(3).decapitalize() }
        val publicProps = this.clazz.memberProperties.filter { it.visibility == KVisibility.PUBLIC }.map { it.name }
        (publicProps + methProps).toSet().toList()
    }

    actual fun construct(vararg constructorArgs: Any?): T {
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

    actual fun allPropertyNames(obj: Any): List<String> {
        if (this.clazz.isInstance(obj)) {
            return this.allPropertyNames
        } else {
            throw RuntimeException("$obj is not an instance of ${this.clazz}")
        }
    }

    actual fun getProperty(propertyName: String, obj: Any): Any? {
        val mprop = obj::class.memberProperties.firstOrNull { propertyName == it.name }
        if (null != mprop) {
            val prop = mprop as KProperty1<Any, *>
            prop.getter.isAccessible = true
            return prop.getter.call(obj)
        } else {
            val mmeth = obj::class.memberFunctions.firstOrNull { it.name == "get${propertyName.capitalize()}" && it.valueParameters.size == 0 }
            if (mmeth == null) {
                throw RuntimeException("Property ${propertyName} not found on object ${obj}")
            } else {
                mmeth.isAccessible = true
                return mmeth.call(obj)
            }
        }
    }

    actual fun setProperty(propertyName: String, obj: Any, value: Any?) {
        val mprop = obj::class.memberProperties.first { propertyName == it.name }
        if (mprop != null) {
            val prop = mprop as KMutableProperty1<Any, Any?>
            prop.setter.isAccessible = true
            prop.setter.call(obj, value)
        } else {
            val mmeth = obj::class.memberFunctions.firstOrNull { it.name == "set${propertyName.capitalize()}" && it.valueParameters.size == 1 }
            //TODO: check type of parameter!
            if (mmeth == null) {
                throw RuntimeException("Property ${propertyName} not settable on object ${obj}")
            } else {
                mmeth.isAccessible = true
                mmeth.call(obj, value)
            }
        }
    }
}

actual object ModuleRegistry {

    actual fun classForName(qualifiedName: String): KClass<*> {
        //TODO: should we register for java also?
        return Class.forName(qualifiedName).kotlin
    }
}