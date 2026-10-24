/**
 * Copyright (C) 2020 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

fun <T> mutableStackOf(vararg elements: T): MutableStack<T> {
    val stack = MutableStack<T>()
    elements.forEach {
        stack.push(it)
    }
    return stack
}

class MutableStack<T>() {
    private val list = mutableListOf<T>()

    val size: Int get() = this.list.size
    val isEmpty: Boolean get() = this.list.size == 0
    val isNotEmpty: Boolean get() = this.list.size != 0
    /** A copy of the elements, bottom first (top of stack last). Later pushes/pops don't change it. */
    val elements: List<T> get() = this.list.toList()

    fun clear() {
        this.list.clear()
    }

    fun push(item: T) {
        list.add(item)
    }

    fun peek(): T = list.last()
    fun peekOrNull(): T? = list.lastOrNull()
    /**
     * The top [n] elements, without removing them, bottom first (top of stack last) - the same order as
     * [elements] and as [pop] (n). Returns a copy, so later pushes/pops don't affect it.
     */
    fun peek(n: Int): List<T> = list.subList(list.size - n, list.size).toList()

    fun pop(): T = list.removeAt(list.lastIndex) //  list.removeLast() //this fails see KT-71375 [https://youtrack.jetbrains.com/issue/KT-71375/Prevent-Kotlins-removeFirst-and-removeLast-from-causing-crashes-on-Android-14-and-below-after-upgrading-to-Android-API-Level-35]
    /**
     * Removes the top [n] elements and returns them bottom first (top of stack last), i.e. in the order they
     * were pushed - the same order as [peek] (n). E.g. after push(1), push(2), push(3): pop(2) == [2, 3].
     *
     * (Before 2.4.0 this returned them top first, [3, 2].)
     */
    fun pop(n: Int): List<T> {
        // check first, so a failing pop leaves the stack unchanged
        if (n < 0 || n > list.size) throw IndexOutOfBoundsException("Cannot pop $n elements from a stack of size ${list.size}")
        val top = list.subList(list.size - n, list.size)
        val removed = top.toList()
        top.clear()
        return removed
    }
}
