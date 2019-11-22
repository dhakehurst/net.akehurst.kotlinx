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

import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

expect class ClassReflection<T : Any>(kclass: KClass<T>) {

    val isAbstract: Boolean

    val allPropertyNames: List<String>

    fun construct(vararg constructorArgs: Any?): T

    fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean

    fun allPropertyNames(self: T): List<String>

    fun getProperty(self: T, propertyName: String): Any?

    fun setProperty(self: T, propertyName: String, value: Any?)

    fun isPropertyMutable(propertyName: String): Boolean

    fun call(self: T, methodName: String, vararg args: Any?) : Any?
}

expect class ObjectReflection<T : Any>(self: T) {

    val kclass: KClass<T>

    val isAbstract: Boolean

    val allPropertyNames: List<String>

    fun construct(vararg constructorArgs: Any?): T

    fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean

    fun getProperty(propertyName: String): Any?

    fun setProperty(propertyName: String, value: Any?)

    fun isPropertyMutable(propertyName: String): Boolean

    fun call(methodName: String, vararg args: Any?) : Any?
}

fun Any.reflect() = ObjectReflection(this)
fun KClass<*>.reflect() = ClassReflection(this)

expect object ModuleRegistry {

    // must be inline so that the generated code can find the module as the imported JS name
    inline fun register(moduleName: String)

    fun classForName(qualifiedName: String): KClass<*>

}
