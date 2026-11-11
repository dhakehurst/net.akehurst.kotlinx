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

/** The iterator of an empty managed collection (its delegate has not been created yet). Behaves like [managedIterator] over an empty collection. */
internal object EmptyManagedIterator : MutableIterator<Nothing> {
    override fun hasNext(): Boolean = false
    override fun next(): Nothing = throw NoSuchElementException()
    override fun remove() = throw IllegalStateException("next() must be called before remove()")
}

/** One shared value: on the JVM every `Reference::class` expression allocates a new ClassReference. */
private val REFERENCE_KCLASS: KClass<*> = Reference::class

// --- collections ---
// The delegate of each collection is created on the first add, because most managed collections stay empty
// (e.g. one per property of every model object). Don't use `by lazy`: a Lazy object costs about as much as it saves.

/**
 * A collection (bag: duplicates allowed, no order guarantees) that checks element types at runtime and calls hooks on mutation.
 *
 * @param name A name for the collection - used in error messages about violations.
 * @param expectedType The type every element must have (checked at runtime).
 * @param onAdded Called after an element is added.
 * @param onRemoved Called after an element is removed.
 */
open class ManagedCollection<E : Any>(
    protected val name: String,
    private val expectedType: KClass<*>,
    private val onAdded: ((E) -> Unit)? = null,
    private val onRemoved: ((E) -> Unit)? = null
) : AbstractMutableCollection<E>() {

    private var _delegate: MutableList<E>? = null

    override val size: Int get() = _delegate?.size ?: 0

    /** Checks an element before it is added. This catches elements that got past the compiler via generic upcasts. */
    protected open fun validateType(element: E) = checkManagedType("Collection", name, expectedType, element)

    override fun add(element: E): Boolean {
        validateType(element)
        val delegate = _delegate ?: mutableListOf<E>().also { _delegate = it }
        val res = delegate.add(element)
        if (res) onAdded?.invoke(element)
        return res
    }

    override fun iterator(): MutableIterator<E> = _delegate?.let { managedIterator(it.iterator(), onRemoved) } ?: EmptyManagedIterator
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
    protected val name: String,
    private val expectedType: KClass<*>,
    private val onAdded: ((E) -> Unit)? = null,
    private val onRemoved: ((E) -> Unit)? = null
) : AbstractMutableList<E>() {

    private var _delegate: MutableList<E>? = null

    /** The delegate, or an [IndexOutOfBoundsException] for any [index] while there is none (the list is empty). */
    private fun delegateFor(index: Int): MutableList<E> =
        _delegate ?: throw IndexOutOfBoundsException("Index $index out of bounds for length 0")

    override val size: Int get() = _delegate?.size ?: 0

    override fun get(index: Int): E = delegateFor(index)[index]

    /** Checks an element before it is added. This catches elements that got past the compiler via generic upcasts. */
    protected open fun validateType(element: E) = checkManagedType("List", name, expectedType, element)

    override fun add(index: Int, element: E) {
        validateType(element)
        val delegate = _delegate ?: mutableListOf<E>().also { _delegate = it }
        delegate.add(index, element)
        onAdded?.invoke(element)
    }

    override fun set(index: Int, element: E): E {
        validateType(element)
        val oldElement = delegateFor(index).set(index, element)
        onRemoved?.invoke(oldElement)
        onAdded?.invoke(element)
        return oldElement
    }

    override fun removeAt(index: Int): E {
        val removedElement = delegateFor(index).removeAt(index)
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
    protected val name: String,
    private val expectedType: KClass<*>,
    private val onAdded: ((E) -> Unit)? = null,
    private val onRemoved: ((E) -> Unit)? = null
) : AbstractMutableSet<E>() {

    private var _delegate: MutableSet<E>? = null

    override val size: Int get() = _delegate?.size ?: 0

    /** Checks an element before it is added. This catches elements that got past the compiler via generic upcasts. */
    protected open fun validateType(element: E) = checkManagedType("Set", name, expectedType, element)

    override fun add(element: E): Boolean {
        validateType(element)
        val delegate = _delegate ?: mutableSetOf<E>().also { _delegate = it }
        val res = delegate.add(element)
        if (res) onAdded?.invoke(element)
        return res
    }

    override fun iterator(): MutableIterator<E> = _delegate?.let { managedIterator(it.iterator(), onRemoved) } ?: EmptyManagedIterator
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
    protected val name: String,
    private val expectedType: KClass<*>,
    private val onAdded: ((E) -> Unit)? = null,
    private val onRemoved: ((E) -> Unit)? = null
) : AbstractMutableSet<E>(), MutableOrderedSet<E> {

    private var _delegate: LinkedHashSet<E>? = null

    override val size: Int get() = _delegate?.size ?: 0

    /** Checks an element before it is added. This catches elements that got past the compiler via generic upcasts. */
    protected open fun validateType(element: E) = checkManagedType("OrderedSet", name, expectedType, element)

    override fun add(element: E): Boolean {
        validateType(element)
        val delegate = _delegate ?: linkedSetOf<E>().also { _delegate = it }
        val res = delegate.add(element)
        if (res) onAdded?.invoke(element)
        return res
    }

    override fun iterator(): MutableIterator<E> = _delegate?.let { managedIterator(it.iterator(), onRemoved) } ?: EmptyManagedIterator

    override fun get(index: Int): E {
        _delegate?.forEachIndexed { i, e ->
            if (index == i) return e
        }
        throw IndexOutOfBoundsException("$index")
    }

    // order matters when compared to another OrderedSet (see OrderedSet); hashCode stays the Set hashCode
    override fun equals(other: Any?): Boolean = orderedSetEquals(other)
    override fun hashCode(): Int = super.hashCode()
}

// --- collections of references: check the type of what each reference resolves to ---
// `name` is a plain constructor parameter here; validateType uses the superclass's `name`.

open class ManagedReferenceCollection<T : Any>(
    name: String,
    private val referenceType: KClass<T>,
    onAdded: ((Reference<Any, T>) -> Unit)? = null,
    onRemoved: ((Reference<Any, T>) -> Unit)? = null
) : ManagedCollection<Reference<Any, T>>(name, REFERENCE_KCLASS, onAdded, onRemoved) {
    override fun validateType(element: Reference<Any, T>) = checkManagedReference("Collection", name, referenceType, element)
}

open class ManagedReferenceList<T : Any>(
    name: String,
    private val referenceType: KClass<T>,
    onAdded: ((Reference<Any, T>) -> Unit)? = null,
    onRemoved: ((Reference<Any, T>) -> Unit)? = null
) : ManagedList<Reference<Any, T>>(name, REFERENCE_KCLASS, onAdded, onRemoved) {
    override fun validateType(element: Reference<Any, T>) = checkManagedReference("List", name, referenceType, element)
}

open class ManagedReferenceSet<T : Any>(
    name: String,
    private val referenceType: KClass<T>,
    onAdded: ((Reference<Any, T>) -> Unit)? = null,
    onRemoved: ((Reference<Any, T>) -> Unit)? = null
) : ManagedSet<Reference<Any, T>>(name, REFERENCE_KCLASS, onAdded, onRemoved) {
    override fun validateType(element: Reference<Any, T>) = checkManagedReference("Set", name, referenceType, element)
}

open class ManagedReferenceOrderedSet<T : Any>(
    name: String,
    private val referenceType: KClass<T>,
    onAdded: ((Reference<Any, T>) -> Unit)? = null,
    onRemoved: ((Reference<Any, T>) -> Unit)? = null
) : ManagedOrderedSet<Reference<Any, T>>(name, REFERENCE_KCLASS, onAdded, onRemoved) {
    override fun validateType(element: Reference<Any, T>) = checkManagedReference("OrderedSet", name, referenceType, element)
}
