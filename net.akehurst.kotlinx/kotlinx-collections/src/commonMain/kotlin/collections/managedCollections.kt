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

/**
 * A custom list that enforces runtime type safety and triggers mutation hooks.
 *
 * @param name A name for the list - assists with informative error reporting about violations.
 * @param expectedType The strict type required (for runtime checking).
 * @param onAdd Callback fired when an element is successfully added.
 * @param onRemove Callback fired when an element is removed.
 */
class ManagedList<E : Any>(
    private val name: String,
    private val expectedType: KClass<*>,
    private val onAdd: ((E) -> Unit)? = null,
    private val onRemove: ((E) -> Unit)? = null
) : AbstractMutableList<E>() {

    // The actual raw data storage
    private val delegate = mutableListOf<E>()

    override val size: Int get() = delegate.size

    override fun get(index: Int): E = delegate[index]

    /**
     * This intercepts elements coming from generic upcasts.
     */
    private fun validateType(element: E) {
        if (!expectedType.isInstance(element)) {
            throw IllegalArgumentException(
                "Managed List violation on '$name': Expected '${expectedType.simpleName}', but got '${element::class.simpleName}'."
            )
        }
    }

    override fun add(index: Int, element: E) {
        validateType(element)
        delegate.add(index, element)
        // Trigger the mutation management
        onAdd?.invoke(element)
    }

    override fun set(index: Int, element: E): E {
        validateType(element)
        val oldElement = delegate.set(index, element)
        onRemove?.invoke(oldElement)
        onAdd?.invoke(element)
        return oldElement
    }

    override fun removeAt(index: Int): E {
        val removedElement = delegate.removeAt(index)
        // Trigger mutation management
        onRemove?.invoke(removedElement)
        return removedElement
    }
}

/**
 * A custom set that enforces runtime type safety and triggers mutation hooks.
 *
 * @param name A name for the set - assists with informative error reporting about violations.
 * @param expectedType The strict type required (for runtime checking).
 * @param onAdd Callback fired when an element is successfully added.
 * @param onRemove Callback fired when an element is removed.
 */
class ManagedSet<E : Any>(
    private val name: String,
    private val expectedType: KClass<*>,
    private val onAdd: ((E) -> Unit)? = null,
    private val onRemove: ((E) -> Unit)? = null
) : AbstractMutableSet<E>() {

    // The actual raw data storage
    private val delegate = mutableSetOf<E>()

    override val size: Int get() = delegate.size

    /**
     * This intercepts elements coming from generic upcasts.
     */
    private fun validateType(element: E) {
        if (!expectedType.isInstance(element)) {
            throw IllegalArgumentException(
                "Managed List violation on '$name': Expected '${expectedType.simpleName}', but got '${element::class.simpleName}'."
            )
        }
    }

    override fun add(element: E): Boolean {
        validateType(element)
        val res = delegate.add(element)
        // Trigger the mutation management
        if(res) onAdd?.invoke(element)
        return res
    }

    override fun iterator(): MutableIterator<E> {
        val baseIterator = delegate.iterator()
        return object : MutableIterator<E> {
            private var lastReturned: E? = null

            override fun hasNext(): Boolean = baseIterator.hasNext()

            override fun next(): E {
                val nextVal = baseIterator.next()
                lastReturned = nextVal
                return nextVal
            }

            override fun remove() {
                val item = lastReturned ?: throw IllegalStateException("next() must be called before remove()")
                baseIterator.remove()
                onRemove?.invoke(item) // Safely fires your SysML reference tracking
                lastReturned = null
            }
        }
    }
}

class ManagedOrderedSet<E : Any>(
    private val name: String,
    private val expectedType: KClass<*>,
    private val onAdd: ((E) -> Unit)? = null,
    private val onRemove: ((E) -> Unit)? = null
) : AbstractMutableSet<E>(), MutableOrderedSet<E> {

    // The actual raw data storage
    private val delegate = linkedSetOf<E>()

    override val size: Int get() = delegate.size

    /**
     * This intercepts elements coming from generic upcasts.
     */
    private fun validateType(element: E) {
        if (!expectedType.isInstance(element)) {
            throw IllegalArgumentException(
                "Managed List violation on '$name': Expected '${expectedType.simpleName}', but got '${element::class.simpleName}'."
            )
        }
    }

    override fun add(element: E): Boolean {
        validateType(element)
        val res = delegate.add(element)
        // Trigger the mutation management
        if(res) onAdd?.invoke(element)
        return res
    }

    override fun iterator(): MutableIterator<E> {
        val baseIterator = delegate.iterator()
        return object : MutableIterator<E> {
            private var lastReturned: E? = null

            override fun hasNext(): Boolean = baseIterator.hasNext()

            override fun next(): E {
                val nextVal = baseIterator.next()
                lastReturned = nextVal
                return nextVal
            }

            override fun remove() {
                val item = lastReturned ?: throw IllegalStateException("next() must be called before remove()")
                baseIterator.remove()
                onRemove?.invoke(item) // Safely fires your SysML reference tracking
                lastReturned = null
            }
        }
    }

    override fun get(index: Int): E {
        delegate.forEachIndexed { i, e ->
            if (index == i) return e
        }
        throw IndexOutOfBoundsException("$index")
    }
}