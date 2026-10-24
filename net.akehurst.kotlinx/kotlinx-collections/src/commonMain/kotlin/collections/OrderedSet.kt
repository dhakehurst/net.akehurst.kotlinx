/*
 * Copyright (C) 2023 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

import kotlin.js.JsName

/**
 * A Set whose elements have an order (insertion order).
 *
 * Equality:
 *  - with another OrderedSet: same elements in the same order.
 *  - with a plain (unordered) Set: same elements, order ignored. A plain Set's own equals ignores order,
 *    so this keeps equals symmetric. It does mean equality is not transitive across ordered and plain sets.
 *
 * hashCode is the plain Set hashCode, so equal sets always have equal hashCodes.
 */
interface OrderedSet<out E> : Set<E> {
    operator fun get(index: Int): E
}

/** The equality rules described on [OrderedSet], shared by all implementations. */
internal fun OrderedSet<*>.orderedSetEquals(other: Any?): Boolean = when {
    this === other -> true
    other !is Set<*> -> false
    this.size != other.size -> false
    other is OrderedSet<*> -> {
        val it1 = this.iterator()
        val it2 = other.iterator()
        while (it1.hasNext()) {
            if (it1.next() != it2.next()) return false
        }
        true
    }

    else -> this.containsAll(other)
}

interface MutableOrderedSet<E> : OrderedSet<E>, MutableSet<E>

fun <T> emptyOrderedSet(): OrderedSet<T> = EmptyOrderedSet
fun <E> orderedSetOf(vararg elements: E): OrderedSet<E> = OrderedSetImpl<E>(elements.toList())
fun <E> mutableOrderedSetOf(vararg elements: E): MutableOrderedSet<E> = MutableOrderedSetImpl<E>(elements.toList())

fun <T> Iterable<T>.toOrderedSet(): OrderedSet<T> = when (this) {
    is Collection<T> -> MutableOrderedSetImpl(this)
    else -> MutableOrderedSetImpl(this.toMutableSet())
}

fun <T> Iterable<T>.toMutableOrderedSet(): MutableOrderedSet<T> = when (this) {
    is Collection<T> -> MutableOrderedSetImpl(this)
    else -> MutableOrderedSetImpl(this.toMutableSet())
}

operator fun <T> OrderedSet<T>.plus(element: T): OrderedSet<T> {
    val result = this.toMutableOrderedSet()
    result.add(element)
    return result
}

@JsName("plusAll")
operator fun <T> OrderedSet<T>.plus(elements: Iterable<T>): OrderedSet<T> {
    val result = this.toMutableList()
    result.addAll(elements)
    return OrderedSetImpl<T>(result)
}

operator fun <T> MutableOrderedSet<T>.plusAssign(elements: Iterable<T>) {
    this.addAll(elements)
}

class OrderedSetImpl<E> : OrderedSet<E> {

    constructor(collection: Collection<E>) {
        this._impl = LinkedHashSet(collection)
    }

    private val _impl: LinkedHashSet<E>

    override fun get(index: Int): E {
        _impl.forEachIndexed { i, e ->
            if (index == i) return e
        }
        throw IndexOutOfBoundsException("$index")
    }

    override val size: Int get() = _impl.size
    override fun iterator(): Iterator<E> = _impl.iterator()
    override fun isEmpty(): Boolean = _impl.isEmpty()
    override fun contains(element: E): Boolean = _impl.contains(element)
    override fun containsAll(elements: Collection<E>): Boolean = _impl.containsAll(elements)

    override fun equals(other: Any?): Boolean = orderedSetEquals(other)

    override fun hashCode() = _impl.hashCode()
    override fun toString() = _impl.toString()
}

class MutableOrderedSetImpl<E> : MutableOrderedSet<E> {

    constructor(collection: Collection<E>) {
        this._impl = LinkedHashSet(collection)
    }

    private val _impl: LinkedHashSet<E>

    override fun get(index: Int): E {
        _impl.forEachIndexed { i, e ->
            if (index == i) return e
        }
        throw IndexOutOfBoundsException("$index")
    }

    override val size: Int get() = _impl.size
    override fun iterator(): MutableIterator<E> = _impl.iterator()
    override fun isEmpty(): Boolean = _impl.isEmpty()
    override fun contains(element: E): Boolean = _impl.contains(element)
    override fun containsAll(elements: Collection<E>): Boolean = _impl.containsAll(elements)

    override fun add(element: E): Boolean = _impl.add(element)
    override fun addAll(elements: Collection<E>): Boolean = _impl.addAll(elements)
    override fun remove(element: E): Boolean = _impl.remove(element)
    override fun removeAll(elements: Collection<E>): Boolean = _impl.removeAll(elements)
    override fun retainAll(elements: Collection<E>): Boolean = _impl.retainAll(elements)
    override fun clear() {
        _impl.clear()
    }

    override fun equals(other: Any?): Boolean = orderedSetEquals(other)

    override fun hashCode() = _impl.hashCode()
    override fun toString() = _impl.toString()
}

internal object EmptyIterator : ListIterator<Nothing> {
    override fun hasNext(): Boolean = false
    override fun hasPrevious(): Boolean = false
    override fun nextIndex(): Int = 0
    override fun previousIndex(): Int = -1
    override fun next(): Nothing = throw NoSuchElementException()
    override fun previous(): Nothing = throw NoSuchElementException()
}

internal object EmptyOrderedSet : OrderedSet<Nothing> {

    override val size: Int get() = 0
    override fun isEmpty(): Boolean = true
    override fun contains(element: Nothing): Boolean = false
    override fun containsAll(elements: Collection<Nothing>): Boolean = elements.isEmpty()

    override fun get(index: Int): Nothing = throw IndexOutOfBoundsException("An empty OrderedSet does not contain element at index $index.")
    override fun iterator(): Iterator<Nothing> = EmptyIterator

    override fun equals(other: Any?): Boolean = other is Set<*> && other.isEmpty()
    override fun hashCode(): Int = 0
    override fun toString(): String = "[]"
}