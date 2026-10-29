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

// --- Derived values ---

/**
 * A value computed from other values each time it is read. It has no stored state and cannot be set.
 */
interface Derived<out T> {
    fun get(): T
}

/** A derived value computed by [derivation] each time [get] is called. */
class DerivedDefault<out T>(
    private val derivation: () -> T
) : Derived<T> {
    override fun get(): T = derivation()
    override fun toString(): String = "Derived"
}

/**
 * A derived value that checks the type of each computed value, like [ManagedValue].
 * `null` is always allowed. [name] is used in error messages.
 */
class ManagedDerived<out T>(
    private val name: String,
    private val expectedType: KClass<*>,
    private val derivation: () -> T
) : Derived<T> {

    override fun get(): T = derivation().also { validateType(it) }

    private fun validateType(element: Any?) {
        if (null != element && !expectedType.isInstance(element)) {
            throw IllegalStateException(
                "Managed Derived violation on '$name': Expected '${expectedType.simpleName}', but derivation gave '${element::class.simpleName}'."
            )
        }
    }

    override fun toString(): String = "ManagedDerived($name)"
}
