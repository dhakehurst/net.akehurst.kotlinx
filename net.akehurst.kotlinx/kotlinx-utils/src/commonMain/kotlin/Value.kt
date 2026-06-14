/*
 * Copyright (C) 2026 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package net.akehurst.kotlinx.utils

import kotlin.reflect.KClass

// --- Mutable values ---
interface Value<out T> {
    fun get(): T
}

data class ValueImmutable<out T>(
    val value: T
) : Value<T> {
    override fun get(): T = value
}

interface MutableValue<T> : Value<T> {
    fun set(value: T)
}

object ValueExt {
    val <T> Value<T>.mutable: MutableValue<T> get() = this as MutableValue<T>
}

abstract class MutableValueAbstract<T>(

) : MutableValue<T> {
    abstract var value: T
    override fun get(): T = value
    override fun set(value: T) {
        this.value = value
    }
}

data class MutableValueDefault<T>(
    override var value: T
) : MutableValueAbstract<T>() {

}

data class ManagedValue<T>(
    override var value: T,
    private val name: String,
    private val expectedType: KClass<*>,
    private val onChanged: ((old:T?, new:T?) -> Unit)? = null,
) : MutableValueAbstract<T>() {

    private fun validateType(element: T) {
        if (!expectedType.isInstance(element)) {
            throw IllegalArgumentException(
                "Managed List violation on '$name': Expected '${expectedType.simpleName}', but got '${element?.let { it::class.simpleName }}'."
            )
        }
    }

    override fun set(value: T) {
        if (this.value != value) {
            val old = this.value
            validateType(value)
            super.set(value)
            onChanged?.invoke(old, value)
        } else {
            // already set
        }
    }
}

