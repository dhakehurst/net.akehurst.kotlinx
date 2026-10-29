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

package net.akehurst.kotlinx.collections

import kotlin.reflect.KClass

// --- derived collections ---
// Read-only collections whose content is computed by a derivation each time they are used.
// Each operation (size, get, iterator, contains, ...) computes the content once, so iterating does not recompute per element.
// Every computed element is checked against the expected type; a wrong type is a bug in the derivation, so it throws IllegalStateException.

/** Throws if [element] is not an instance of [expectedType]. [kind] (e.g. "List") is used in the message. */
internal fun checkDerivedType(kind: String, name: String, expectedType: KClass<*>, element: Any) {
    if (!expectedType.isInstance(element)) {
        throw IllegalStateException(
            "Derived $kind violation on '$name': Expected '${expectedType.simpleName}', but derivation gave '${element::class.simpleName}'."
        )
    }
}

private fun <C : Collection<Any>> C.checked(kind: String, name: String, expectedType: KClass<*>): C =
    also { it.forEach { e -> checkDerivedType(kind, name, expectedType, e) } }

/**
 * A derived collection (bag: duplicates allowed, no order guarantees).
 *
 * @param name A name for the collection - used in error messages.
 * @param expectedType The type every element must have (checked at runtime).
 * @param derivation Computes the content.
 */
open class DerivedCollection<E : Any>(
    private val name: String,
    private val expectedType: KClass<*>,
    private val derivation: () -> Collection<E>
) : AbstractCollection<E>() {
    protected fun compute(): Collection<E> = derivation().checked("Collection", name, expectedType)
    override val size: Int get() = compute().size
    override fun iterator(): Iterator<E> = compute().iterator()
    override fun toString(): String = "DerivedCollection($name)"
}

/** A derived list. See [DerivedCollection] for the parameters. */
open class DerivedList<E : Any>(
    private val name: String,
    private val expectedType: KClass<*>,
    private val derivation: () -> List<E>
) : AbstractList<E>() {
    protected fun compute(): List<E> = derivation().checked("List", name, expectedType)
    override val size: Int get() = compute().size
    override fun get(index: Int): E = compute()[index]
    override fun iterator(): Iterator<E> = compute().iterator()
    override fun listIterator(): ListIterator<E> = compute().listIterator()
    override fun listIterator(index: Int): ListIterator<E> = compute().listIterator(index)
    override fun toString(): String = "DerivedList($name)"
}

/** A derived set. See [DerivedCollection] for the parameters. */
open class DerivedSet<E : Any>(
    private val name: String,
    private val expectedType: KClass<*>,
    private val derivation: () -> Set<E>
) : AbstractSet<E>() {
    protected fun compute(): Set<E> = derivation().checked("Set", name, expectedType)
    override val size: Int get() = compute().size
    override fun iterator(): Iterator<E> = compute().iterator()
    override fun contains(element: E): Boolean = compute().contains(element)
    override fun toString(): String = "DerivedSet($name)"
}

/** A derived [OrderedSet]. See [DerivedCollection] for the parameters. */
open class DerivedOrderedSet<E : Any>(
    private val name: String,
    private val expectedType: KClass<*>,
    private val derivation: () -> OrderedSet<E>
) : AbstractSet<E>(), OrderedSet<E> {
    protected fun compute(): OrderedSet<E> = derivation().checked("OrderedSet", name, expectedType)
    override val size: Int get() = compute().size
    override fun get(index: Int): E = compute()[index]
    override fun iterator(): Iterator<E> = compute().iterator()
    override fun contains(element: E): Boolean = compute().contains(element)
    override fun equals(other: Any?): Boolean = orderedSetEquals(other)
    override fun hashCode(): Int = compute().hashCode()
    override fun toString(): String = "DerivedOrderedSet($name)"
}
