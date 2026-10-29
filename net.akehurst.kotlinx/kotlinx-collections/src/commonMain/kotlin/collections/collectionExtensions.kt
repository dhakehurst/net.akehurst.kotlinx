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

fun <E> List<E>.indexOfOrNull(element: E) = indexOf(element).let { if (-1 == it) null else it }

internal fun <T> Set<T>.transitiveClosure_old(function: (T) -> Set<T>): Set<T> {
    val result: MutableSet<T> = this.toMutableSet()
    var oldResult: MutableSet<T> = mutableSetOf<T>()
    while (!oldResult.containsAll(result)) {
        oldResult = result.toMutableSet()
        for (nt: T in oldResult) {
            val s: Set<T> = function.invoke(nt)
            result.addAll(s)
        }
    }
    return result
}

fun IntArray.including(value: Int): IntArray {
    return if (this.contains(value)) {
        this
    } else {
        val na = this.copyOf(this.size + 1)
        na[this.size] = value
        na
    }
}