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

package net.akehurst.kotlinx.collections

/**
 * An immutable stack. push and pop return a new Stack and leave this one unchanged.
 *
 * Stored as a chain of nodes (each points to the one below it), so push and pop are O(1)
 * and stacks share their lower nodes.
 */
class Stack<T> private constructor(
    private val top: Node<T>?,
    val size: Int
) {

    private class Node<T>(val item: T, val below: Node<T>?)

    class PopResult<T>(
        val item: T,
        val stack: Stack<T>
    )

    /** A stack holding [elements], with the last element on top. The list is copied. */
    constructor(elements: List<T> = emptyList()) : this(pushNodes(null, elements), elements.size)

    private companion object {
        fun <T> pushNodes(base: Node<T>?, items: List<T>): Node<T>? = items.fold(base) { below, item -> Node(item, below) }
    }

    /** The elements, bottom first (top of stack last). */
    val elements: List<T> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val list = ArrayList<T>(size)
        var n = top
        while (null != n) {
            list.add(n.item)
            n = n.below
        }
        list.reverse()
        list
    }

    val isEmpty: Boolean get() = this.size == 0
    val isNotEmpty: Boolean get() = this.size != 0

    fun push(item: T): Stack<T> = Stack(Node(item, top), size + 1)
    fun pushAll(items: List<T>): Stack<T> = Stack(pushNodes(top, items), size + items.size)
    fun peek(): T = (top ?: throw NoSuchElementException("Stack is empty.")).item
    fun peekOrNull(): T? = top?.item
    fun pop(): PopResult<T> {
        val t = top ?: throw NoSuchElementException("Stack is empty.")
        return PopResult(t.item, Stack(t.below, size - 1))
    }

    fun clone() = Stack(top, size)

    override fun hashCode(): Int = elements.hashCode()
    override fun equals(other: Any?): Boolean {
        if (other !is Stack<*>) return false
        if (this.size != other.size) return false
        var a = this.top
        var b = other.top
        while (null != a && null != b) {
            if (a === b) return true // shared nodes below here
            if (a.item != b.item) return false
            a = a.below
            b = b.below
        }
        return true
    }

    override fun toString(): String = "Stack$elements"
}