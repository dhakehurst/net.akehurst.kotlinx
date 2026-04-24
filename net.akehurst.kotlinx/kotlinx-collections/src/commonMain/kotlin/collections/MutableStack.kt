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
    val elements: List<T> get() = this.list

    fun clear() {
        this.list.clear()
    }

    fun push(item: T) {
        list.add(item)
    }

    fun peek(): T = list.last()
    fun peekOrNull(): T? = list.lastOrNull()
    fun peek(n: Int): List<T> = list.subList(list.size - n, list.size)

    fun pop(): T = list.removeAt(list.lastIndex) //  list.removeLast() //this fails see KT-71375 [https://youtrack.jetbrains.com/issue/KT-71375/Prevent-Kotlins-removeFirst-and-removeLast-from-causing-crashes-on-Android-14-and-below-after-upgrading-to-Android-API-Level-35]
    fun pop(n: Int): List<T> {
        val removed = mutableListOf<T>()
        for (i in 0 until n) {
            removed.add(list.removeAt(list.lastIndex))
        }
        return removed
    }
}
