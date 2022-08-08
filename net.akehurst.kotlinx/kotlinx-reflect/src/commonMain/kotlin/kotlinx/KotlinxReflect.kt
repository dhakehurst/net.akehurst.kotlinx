/**
 * Copyright (C) 2022 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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


typealias EnumValuesFunction = ()->Array<Enum<*>>

object KotlinxReflect {

    private var _registeredClasses = mutableMapOf<String, KClass<*>>()
    private var _registeredClassesReverse = mutableMapOf<KClass<*>,String>()
    private var _enumValuesFunction = mutableMapOf<String,EnumValuesFunction>()

    val registeredClasses:Map<String, KClass<*>> = _registeredClasses

    fun registerClass(qualifiedName: String, cls: KClass<*>, enumValuesFunction: EnumValuesFunction? = null) {
        _registeredClasses[qualifiedName] = cls
        _registeredClassesReverse[cls]=qualifiedName
        if (null!=enumValuesFunction) {
            _enumValuesFunction[qualifiedName] = enumValuesFunction
        }
    }

    fun classForName(qualifiedName: String): KClass<*> {
        return registeredClasses[qualifiedName] ?:error("Cannot find class $qualifiedName, is the class registered with KotlinxReflect?")
    }

    fun qualifiedNameForClass(kclass: KClass<*>): String {
        return _registeredClassesReverse[kclass]
            ?: error("Cannot get qualifiedName of '${kclass}' is it registered with KotlinxReflect?")
    }

    fun <E:Enum<E>> enumValues(qualifiedName: String): List<E> = _enumValuesFunction[qualifiedName]?.invoke()?.asList() as List<E>? ?: emptyList()
}