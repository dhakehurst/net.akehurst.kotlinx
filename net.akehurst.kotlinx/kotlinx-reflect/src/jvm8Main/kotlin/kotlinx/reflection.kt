/**
 * Copyright (C) 2019 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.akehurst.kotlinx.reflect

import net.akehurst.kotlinx.collections.transitveClosure
import kotlin.reflect.*
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.isAccessible

actual class ClassReflection<T : Any> actual constructor(val kclass: KClass<T>) {

    actual val isAbstract: Boolean = this.kclass.isAbstract

    actual val allPropertyNames: List<String> by lazy {
        //find get methods, for java defined classes/properties
        val methProps = this.kclass.memberFunctions.filter { it.name.startsWith("get") && it.valueParameters.size == 0 }.map { it.name.substring(3).decapitalize() }
        val publicProps = this.kclass.memberProperties.filter { it.visibility == KVisibility.PUBLIC }.map { it.name }
        (publicProps + methProps).toSet().toList()
    }

    actual fun construct(vararg constructorArgs: Any?): T {
        return this.kclass.constructors.first {
            //TODO: check types match
            it.parameters.size == constructorArgs.size
        }.call(*constructorArgs)
    }

    actual fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean {
        return this.kclass == subtype
                || subtype.supertypes.toSet().transitveClosure {
            val cls = it.classifier
            if (cls is KClass<*>) {
                cls.supertypes.toSet()
            } else {
                emptySet<KType>()
            }
        }.any { it.classifier == this.kclass }
    }

    actual fun allPropertyNames(self: T): List<String> {
        if (this.kclass.isInstance(self)) {
            return this.allPropertyNames
        } else {
            throw RuntimeException("$self is not an instance of ${this.kclass}")
        }
    }

    actual fun isPropertyMutable(propertyName: String): Boolean {
        val mprop = this.kclass.memberProperties.firstOrNull { propertyName == it.name }
        if (null != mprop) {
            return mprop is KMutableProperty1<*, *>
        } else {
            val mmeth = this.kclass.memberFunctions.firstOrNull { it.name == "set${propertyName.capitalize()}" && it.valueParameters.size == 1 }
            return mmeth != null
        }
    }

    actual fun getProperty(self: T, propertyName: String): Any? {
        val mprop = self::class.memberProperties.firstOrNull { propertyName == it.name }
        if (null != mprop) {
            val prop = mprop as KProperty1<Any, *>
            prop.getter.isAccessible = true
            return prop.getter.call(self)
        } else {
            val mmeth = self::class.memberFunctions.firstOrNull { it.name == "get${propertyName.capitalize()}" && it.valueParameters.size == 0 }
            if (mmeth == null) {
                throw RuntimeException("Property ${propertyName} not found on object ${self}")
            } else {
                mmeth.isAccessible = true
                return mmeth.call(self)
            }
        }
    }

    actual fun setProperty(self: T, propertyName: String, value: Any?) {
        val mprop = self::class.memberProperties.firstOrNull { propertyName == it.name }
        if (mprop != null) {
            val prop = mprop as KMutableProperty1<Any, Any?>
            prop.setter.isAccessible = true
            prop.setter.call(self, value)
        } else {
            val mmeth = self::class.memberFunctions.firstOrNull { it.name == "set${propertyName.capitalize()}" && it.valueParameters.size == 1 }
            //TODO: check type of parameter!
            if (mmeth == null) {
                throw RuntimeException("Property ${propertyName} not settable on object ${self}")
            } else {
                mmeth.isAccessible = true
                mmeth.call(self, value)
            }
        }
    }

    actual fun call(self: T, methodName: String, vararg args: Any?): Any? {
        val mem = kclass.memberFunctions.firstOrNull { methodName == it.name }
        if (null != mem) {
            val m = mem as KCallable<*>
            return m.call(self, *args)
        } else {
            throw RuntimeException("Method ${methodName} not found on object ${self}")
        }
    }
}

actual class ObjectReflection<T : Any> actual constructor(val self: T) {

    actual val kclass: KClass<T> = self::class as KClass<T>
    actual val isAbstract: Boolean = this.kclass.isAbstract

    actual val allPropertyNames: List<String> by lazy {
        //find get methods, for java defined classes/properties
        val methProps = this.kclass.memberFunctions.filter { it.name.startsWith("get") && it.valueParameters.size == 0 }.map { it.name.substring(3).decapitalize() }
        val publicProps = this.kclass.memberProperties.filter { it.visibility == KVisibility.PUBLIC }.map { it.name }
        (publicProps + methProps).toSet().toList()
    }

    actual fun construct(vararg constructorArgs: Any?): T {
        return this.kclass.constructors.first {
            //TODO: check types match
            it.parameters.size == constructorArgs.size
        }.call(*constructorArgs)
    }

    actual fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean {
        return this.kclass == subtype
                || subtype.supertypes.toSet().transitveClosure {
            val cls = it.classifier
            if (cls is KClass<*>) {
                cls.supertypes.toSet()
            } else {
                emptySet<KType>()
            }
        }.any { it.classifier == this.kclass }
    }

    actual fun isPropertyMutable(propertyName: String): Boolean {
        val mprop = this.kclass.memberProperties.firstOrNull { propertyName == it.name }
        if (null != mprop) {
            return mprop is KMutableProperty1<*, *>
        } else {
            val mmeth = this.kclass.memberFunctions.firstOrNull { it.name == "set${propertyName.capitalize()}" && it.valueParameters.size == 1 }
            return mmeth != null
        }
    }

    actual fun getProperty(propertyName: String): Any? {
        val mprop = kclass.memberProperties.firstOrNull { propertyName == it.name }
        if (null != mprop) {
            val prop = mprop as KProperty1<Any, *>
            prop.getter.isAccessible = true
            return prop.getter.call(self)
        } else {
            val mmeth = kclass.memberFunctions.firstOrNull { it.name == "get${propertyName.capitalize()}" && it.valueParameters.size == 0 }
            if (mmeth == null) {
                throw RuntimeException("Property ${propertyName} not found on object ${self}")
            } else {
                mmeth.isAccessible = true
                return mmeth.call(self)
            }
        }
    }

    actual fun setProperty(propertyName: String, value: Any?) {
        val mprop = kclass.memberProperties.firstOrNull { propertyName == it.name }
        if (mprop != null) {
            val prop = mprop as KMutableProperty1<Any, Any?>
            prop.setter.isAccessible = true
            prop.setter.call(self, value)
        } else {
            val mmeth = kclass.memberFunctions.firstOrNull { it.name == "set${propertyName.capitalize()}" && it.valueParameters.size == 1 }
            //TODO: check type of parameter!
            if (mmeth == null) {
                throw RuntimeException("Property ${propertyName} not settable on object ${self}")
            } else {
                mmeth.isAccessible = true
                mmeth.call(self, value)
            }
        }
    }

    actual fun call(methodName: String, vararg args: Any?): Any? {
        val mem = kclass.memberFunctions.firstOrNull { methodName == it.name }
        if (null != mem) {
            val m = mem as KCallable<*>
            return m.call(self, *args)
        } else {
            throw RuntimeException("Method ${methodName} not found on object ${self}")
        }
    }
}

actual object ModuleRegistry {

    actual inline fun register(moduleName: String) {
        //Not needed for JVM
    }

    actual fun classForName(qualifiedName: String): KClass<*> {
        //TODO: should we register for java also?
        return Class.forName(qualifiedName).kotlin
    }
}

