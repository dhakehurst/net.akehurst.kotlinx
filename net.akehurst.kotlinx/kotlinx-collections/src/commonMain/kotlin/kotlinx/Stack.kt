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

class Stack<T>(
    val elements: List<T> = emptyList()
) {

    class PopResult<T>(
        val item: T,
        val stack: Stack<T>
    )

    val size: Int get() = this.elements.size
    val isEmpty: Boolean get() = this.elements.size == 0
    val isNotEmpty: Boolean get() = this.elements.size != 0

    fun push(item: T): Stack<T> = Stack(elements + item)
    fun pushAll(items: List<T>): Stack<T> = Stack(this.elements + items)
    fun peek(): T = elements.last()
    fun peekOrNull(): T? = elements.lastOrNull()
    fun pop(): PopResult<T> = PopResult(this.peek(), Stack(elements.subList(0, size - 1)))
    fun clone() = Stack(elements)

    override fun hashCode(): Int = elements.hashCode()
    override fun equals(other: Any?): Boolean = when (other) {
        !is Stack<*> -> false
        else -> this.elements == other.elements
    }
}