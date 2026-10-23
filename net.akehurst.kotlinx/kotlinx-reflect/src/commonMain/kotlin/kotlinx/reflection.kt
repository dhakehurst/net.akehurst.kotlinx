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

@file:JvmMultifileClass
@file:JvmName("ReflectionKt")

package net.akehurst.kotlinx.reflect

import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

expect fun KFunction<*>.isSuspend(): Boolean

expect fun <T : Any> proxyFor(forInterface: KClass<*>, invokeMethod: (handler: Any, proxy: Any?, callable: KCallable<*>, methodName: String, args: Array<out Any>) -> Any?): T

fun Any.reflect(): ObjectReflection<Any> = ObjectReflection(this)

@Suppress("UNCHECKED_CAST")
fun KClass<*>.reflect(): ClassReflection<*> = ClassReflection(this as KClass<Any>)

interface KotlinxReflectModuleRegistry {
    fun registerClasses()
}

class ClassReflection<T : Any>(val kclass: KClass<T>) {

    val qualifiedName: String get() = KotlinxReflect.registeredClass(kclass)?.qualifiedName ?: backend.qualifiedName(kclass)

    val isAbstract: Boolean get() = backend.isAbstract(kclass)

    val isInterface: Boolean get() = backend.isInterface(kclass)

    val isEnum: Boolean get() = backend.isEnum(kclass)

    val isObject: Boolean get() = backend.isObject(kclass)

    val allPropertyNames: List<String> get() = backend.propertyNames(kclass)

    val allMemberFunctions: List<KFunction<*>> get() = backend.memberFunctions(kclass)

    /** For an `object` declaration, returns its instance; otherwise calls the constructor that takes this many arguments. */
    @Suppress("UNCHECKED_CAST")
    fun construct(vararg constructorArgs: Any?): T {
        val b = backend
        return when {
            b.isObject(kclass) -> (b.objectInstance(kclass) ?: ReflectErrors.objectInstanceNotFound(kclass)) as T
            else -> b.construct(kclass, constructorArgs.asList()) as T
        }
    }

    /** True if [subtype] is this class, or inherits from it directly or indirectly. */
    fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean {
        if (kclass == subtype) return true
        val b = backend
        val seen = mutableSetOf<KClass<*>>()
        var level = b.directSupertypes(subtype)
        while (level.isNotEmpty()) {
            if (level.contains(kclass)) return true
            level = level.filter { seen.add(it) }.flatMap { b.directSupertypes(it) }
        }
        return false
    }

    fun allPropertyNamesFor(self: T): List<String> {
        check(kclass.isInstance(self)) { "$self is not an instance of ${kclass.simpleName}" }
        return allPropertyNames
    }

    fun getProperty(self: T, propertyName: String): Any? = backend.getProperty(kclass, self, propertyName)

    fun setProperty(self: T, propertyName: String, value: Any?) = backend.setProperty(kclass, self, propertyName, value)

    /** False if the class has no property with that name. */
    fun isPropertyMutable(propertyName: String): Boolean = backend.isPropertyMutable(kclass, propertyName) ?: false

    @Suppress("UNCHECKED_CAST")
    fun <E : Enum<E>> enumValues(): List<E> = backend.enumValues(kclass) as List<E>

    fun <E : Enum<E>> enumValueOf(name: String): E? = this.enumValues<E>().firstOrNull { it.name == name }

    fun call(self: T, methodName: String, vararg args: Any?): Any? = backend.call(kclass, self, methodName, args.asList())

    suspend fun callSuspend(self: T, methodName: String, vararg args: Any?): Any? = backend.callSuspend(kclass, self, methodName, args.asList())
}

class ObjectReflection<T : Any>(val self: T) {

    @Suppress("UNCHECKED_CAST")
    val kclass: KClass<T> = self::class as KClass<T>

    private val cls = ClassReflection(kclass)

    val isAbstract: Boolean get() = cls.isAbstract

    val isProxy: Boolean get() = backend.isProxy(self)

    val allPropertyNames: List<String> get() = cls.allPropertyNames

    /** Creates a new instance of the same class as [self]. */
    fun construct(vararg constructorArgs: Any?): T = cls.construct(*constructorArgs)

    fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean = cls.isSupertypeOf(subtype)

    fun getProperty(propertyName: String): Any? = cls.getProperty(self, propertyName)

    fun setProperty(propertyName: String, value: Any?) = cls.setProperty(self, propertyName, value)

    fun isPropertyMutable(propertyName: String): Boolean = cls.isPropertyMutable(propertyName)

    fun call(methodName: String, vararg args: Any?): Any? = cls.call(self, methodName, *args)

    suspend fun callSuspend(methodName: String, vararg args: Any?): Any? = cls.callSuspend(self, methodName, *args)
}
