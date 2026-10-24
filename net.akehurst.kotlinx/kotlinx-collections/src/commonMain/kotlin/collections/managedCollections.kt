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

import net.akehurst.kotlinx.utils.Reference
import kotlin.reflect.KClass

// --- shared helpers ---

/** Throws if [element] is not an instance of [expectedType]. [kind] (e.g. "List") is used in the message. */
internal fun checkManagedType(kind: String, name: String, expectedType: KClass<*>, element: Any) {
    if (!expectedType.isInstance(element)) {
        throw IllegalArgumentException(
            "Managed $kind violation on '$name': Expected '${expectedType.simpleName}', but got '${element::class.simpleName}'."
        )
    }
}

/** Throws if [element] is resolved to something that is not an instance of [referenceType]. Unresolved references are accepted. */
internal fun <T : Any> checkManagedReference(kind: String, name: String, referenceType: KClass<T>, element: Reference<Any, T>) {
    val resolvedValue = element.resolved
    if (resolvedValue != null && !referenceType.isInstance(resolvedValue)) {
        throw IllegalArgumentException(
            "Managed Reference $kind violation on '$name': Expected reference to '${referenceType.simpleName}', but got '${resolvedValue::class.simpleName}'."
        )
    }
}

/** Wraps [base] so that removing through the iterator also calls [onRemoved]. */
internal fun <E : Any> managedIterator(base: MutableIterator<E>, onRemoved: ((E) -> Unit)?): MutableIterator<E> =
    object : MutableIterator<E> {
        private var lastReturned: E? = null

        override fun hasNext(): Boolean = base.hasNext()

        override fun next(): E {
            val nextVal = base.next()
            lastReturned = nextVal
            return nextVal
        }

        override fun remove() {
            val item = lastReturned ?: throw IllegalStateException("next() must be called before remove()")
            base.remove()
            onRemoved?.invoke(item)
            lastReturned = null
        }
    }

// --- collections ---

/**
 * A collection (bag: duplicates allowed, no order guarantees) that checks element types at runtime and calls hooks on mutation.
 *
 * @param name A name for the collection - used in error messages about violations.
 * @param expectedType The type every element must have (checked at runtime).
 * @param onAdded Called after an element is added.
 * @param onRemoved Called after an element is removed.
 */
open class ManagedCollection<E : Any>(
    private val name: String,
    private val expectedType: KClass<*>,
    private val onAdded: ((E) -> Unit)? = null,
    private val onRemoved: ((E) -> Unit)? = null
) : AbstractMutableCollection<E>() {

    private val delegate = mutableListOf<E>()

    override val size: Int get() = delegate.size

    /** Checks an element before it is added. This catches elements that got past the compiler via generic upcasts. */
    protected open fun validateType(element: E) = checkManagedType("Collection", name, expectedType, element)

    override fun add(element: E): Boolean {
        validateType(element)
        val res = delegate.add(element)
        if (res) onAdded?.invoke(element)
        return res
    }

    override fun iterator(): MutableIterator<E> = managedIterator(delegate.iterator(), onRemoved)
}

/**
 * A list that checks element types at runtime and calls hooks on mutation.
 *
 * @param name A name for the list - used in error messages about violations.
 * @param expectedType The type every element must have (checked at runtime).
 * @param onAdded Called after an element is added (including the new element of a [set]).
 * @param onRemoved Called after an element is removed (including the old element of a [set]).
 */
open class ManagedList<E : Any>(
    private val name: String,
    private val expectedType: KClass<*>,
    private val onAdded: ((E) -> Unit)? = null,
    private val onRemoved: ((E) -> Unit)? = null
) : AbstractMutableList<E>() {

    private val delegate = mutableListOf<E>()

    override val size: Int get() = delegate.size

    override fun get(index: Int): E = delegate[index]

    /** Checks an element before it is added. This catches elements that got past the compiler via generic upcasts. */
    protected open fun validateType(element: E) = checkManagedType("List", name, expectedType, element)

    override fun add(index: Int, element: E) {
        validateType(element)
        delegate.add(index, element)
        onAdded?.invoke(element)
    }

    override fun set(index: Int, element: E): E {
        validateType(element)
        val oldElement = delegate.set(index, element)
        onRemoved?.invoke(oldElement)
        onAdded?.invoke(element)
        return oldElement
    }

    override fun removeAt(index: Int): E {
        val removedElement = delegate.removeAt(index)
        onRemoved?.invoke(removedElement)
        return removedElement
    }
}

/**
 * A set that checks element types at runtime and calls hooks on mutation.
 *
 * @param name A name for the set - used in error messages about violations.
 * @param expectedType The type every element must have (checked at runtime).
 * @param onAdded Called after an element is added (not for an element that was already present).
 * @param onRemoved Called after an element is removed.
 */
open class ManagedSet<E : Any>(
    private val name: String,
    private val expectedType: KClass<*>,
    private val onAdded: ((E) -> Unit)? = null,
    private val onRemoved: ((E) -> Unit)? = null
) : AbstractMutableSet<E>() {

    private val delegate = mutableSetOf<E>()

    override val size: Int get() = delegate.size

    /** Checks an element before it is added. This catches elements that got past the compiler via generic upcasts. */
    protected open fun validateType(element: E) = checkManagedType("Set", name, expectedType, element)

    override fun add(element: E): Boolean {
        validateType(element)
        val res = delegate.add(element)
        if (res) onAdded?.invoke(element)
        return res
    }

    override fun iterator(): MutableIterator<E> = managedIterator(delegate.iterator(), onRemoved)
}

/**
 * An [OrderedSet] (insertion order) that checks element types at runtime and calls hooks on mutation.
 *
 * @param name A name for the set - used in error messages about violations.
 * @param expectedType The type every element must have (checked at runtime).
 * @param onAdded Called after an element is added (not for an element that was already present).
 * @param onRemoved Called after an element is removed.
 */
open class ManagedOrderedSet<E : Any>(
    private val name: String,
    private val expectedType: KClass<*>,
    private val onAdded: ((E) -> Unit)? = null,
    private val onRemoved: ((E) -> Unit)? = null
) : AbstractMutableSet<E>(), MutableOrderedSet<E> {

    private val delegate = linkedSetOf<E>()

    override val size: Int get() = delegate.size

    /** Checks an element before it is added. This catches elements that got past the compiler via generic upcasts. */
    protected open fun validateType(element: E) = checkManagedType("OrderedSet", name, expectedType, element)

    override fun add(element: E): Boolean {
        validateType(element)
        val res = delegate.add(element)
        if (res) onAdded?.invoke(element)
        return res
    }

    override fun iterator(): MutableIterator<E> = managedIterator(delegate.iterator(), onRemoved)

    override fun get(index: Int): E {
        delegate.forEachIndexed { i, e ->
            if (index == i) return e
        }
        throw IndexOutOfBoundsException("$index")
    }

    // order matters when compared to another OrderedSet (see OrderedSet); hashCode stays the Set hashCode
    override fun equals(other: Any?): Boolean = orderedSetEquals(other)
    override fun hashCode(): Int = super.hashCode()
}

// --- collections of references: check the type of what each reference resolves to ---

open class ManagedReferenceCollection<T : Any>(
    private val name: String,
    private val referenceType: KClass<T>,
    onAdded: ((Reference<Any, T>) -> Unit)? = null,
    onRemoved: ((Reference<Any, T>) -> Unit)? = null
) : ManagedCollection<Reference<Any, T>>(name, Reference::class, onAdded, onRemoved) {
    override fun validateType(element: Reference<Any, T>) = checkManagedReference("Collection", name, referenceType, element)
}

open class ManagedReferenceList<T : Any>(
    private val name: String,
    private val referenceType: KClass<T>,
    onAdded: ((Reference<Any, T>) -> Unit)? = null,
    onRemoved: ((Reference<Any, T>) -> Unit)? = null
) : ManagedList<Reference<Any, T>>(name, Reference::class, onAdded, onRemoved) {
    override fun validateType(element: Reference<Any, T>) = checkManagedReference("List", name, referenceType, element)
}

open class ManagedReferenceSet<T : Any>(
    private val name: String,
    private val referenceType: KClass<T>,
    onAdded: ((Reference<Any, T>) -> Unit)? = null,
    onRemoved: ((Reference<Any, T>) -> Unit)? = null
) : ManagedSet<Reference<Any, T>>(name, Reference::class, onAdded, onRemoved) {
    override fun validateType(element: Reference<Any, T>) = checkManagedReference("Set", name, referenceType, element)
}

open class ManagedReferenceOrderedSet<T : Any>(
    private val name: String,
    private val referenceType: KClass<T>,
    onAdded: ((Reference<Any, T>) -> Unit)? = null,
    onRemoved: ((Reference<Any, T>) -> Unit)? = null
) : ManagedOrderedSet<Reference<Any, T>>(name, Reference::class, onAdded, onRemoved) {
    override fun validateType(element: Reference<Any, T>) = checkManagedReference("OrderedSet", name, referenceType, element)
}
