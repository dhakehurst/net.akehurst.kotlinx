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

import kotlin.reflect.KClass

expect class Reflection<T : Any>(clazz: KClass<T>) {

    val isAbstract: Boolean

    val allPropertyNames: List<String>

    fun construct(vararg constructorArgs: Any?): T

    fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean

    fun allPropertyNames(obj: Any): List<String>

    fun getProperty(propertyName: String, obj: Any): Any?

    fun setProperty(propertyName: String, obj: Any, value: Any?)
}

fun KClass<*>.reflect() = Reflection(this)

expect object ModuleRegistry {

    fun register(moduleName:String)

    fun classForName(qualifiedName:String) :KClass<*>

}