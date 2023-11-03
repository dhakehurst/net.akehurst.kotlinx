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

import kotlin.js.JsExport
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

expect fun KFunction<*>.isSuspend(): Boolean

expect fun <T : Any> proxyFor(forInterface: KClass<*>, invokeMethod: (handler: Any, proxy: Any?, callable: KCallable<*>, methodName: String, args: Array<out Any>) -> Any?): T

expect fun Any.reflect(): ObjectReflection<Any>
expect fun KClass<*>.reflect(): ClassReflection<*>

expect class ClassReflection<T : Any>(kclass: KClass<T>) {

    val qualifiedName: String

    val isAbstract: Boolean

    val allPropertyNames: List<String>

    val allMemberFunctions: List<KFunction<*>>

    fun construct(vararg constructorArgs: Any?): T

    fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean

    fun allPropertyNamesFor(self: T): List<String>

    // fun allMemberFunctionsFor(self: T): List<KFunction<*>>

    fun getProperty(self: T, propertyName: String): Any?

    fun setProperty(self: T, propertyName: String, value: Any?)

    fun isPropertyMutable(propertyName: String): Boolean

    val isEnum: Boolean
    val isObject: Boolean

    fun <E : Enum<E>> enumValues(): List<E>

    fun <E : Enum<E>> enumValueOf(name: String): E?

    fun call(self: T, methodName: String, vararg args: Any?): Any?
}

expect class ObjectReflection<T : Any>(self: T) {

    val kclass: KClass<T>

    val isAbstract: Boolean

    val isProxy: Boolean

    val allPropertyNames: List<String>

    fun construct(vararg constructorArgs: Any?): T

    fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean

    fun getProperty(propertyName: String): Any?

    fun setProperty(propertyName: String, value: Any?)

    fun isPropertyMutable(propertyName: String): Boolean

    fun call(methodName: String, vararg args: Any?): Any?

    suspend fun callSuspend(methodName: String, vararg args: Any?): Any?
}

interface KotlinxReflectModuleRegistry {
    fun registerClasses()
}
