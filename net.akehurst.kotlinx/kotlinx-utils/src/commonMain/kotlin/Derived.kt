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

/**
 * A derived value that can also be set: [set] writes the stored values it is derived from,
 * so that the derivation then gives the new value. Use it only where such an inverse exists;
 * a derived value that cannot be set is a plain [Derived].
 */
interface MutableDerived<T> : Derived<T> {
    fun set(value: T)
}

object DerivedExt {
    inline val <T> Derived<T>.mutable: MutableDerived<T> get() = this as MutableDerived<T>
}

/**
 * A [ManagedDerived] that can be set. [inverse] updates the stored values so that [derivation] gives the new value.
 */
class ManagedMutableDerived<T>(
    private val name: String,
    expectedType: KClass<*>,
    derivation: () -> T,
    private val inverse: (T) -> Unit
) : MutableDerived<T> {

    private val derived = ManagedDerived(name, expectedType, derivation)

    override fun get(): T = derived.get()
    override fun set(value: T) = inverse(value)

    override fun toString(): String = "ManagedMutableDerived($name)"
}
