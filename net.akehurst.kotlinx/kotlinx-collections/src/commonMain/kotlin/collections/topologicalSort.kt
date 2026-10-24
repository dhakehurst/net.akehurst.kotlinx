/**
 * Copyright (C) 2025 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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
 * A Partial Order Sort, because sometimes things are unrelated but still need sorting.
 * Using a Topological Sort from Kahn's Algorithm [https://en.wikipedia.org/wiki/Topological_sorting]
 *
 * `comparator.compare(a, b) > 0` means 'a' must come after 'b'; 0 means unrelated.
 * Elements are tracked by position, so equal (or duplicate) elements are all kept.
 * Unrelated elements keep their input order. Fails if the ordering contains a cycle.
 */
fun <E> Collection<E>.topologicalSort(comparator: Comparator<in E>): List<E> {
    //TODO: perhaps can find something faster!
    val elements = this.toList()
    val n = elements.size

    // dependents[j] = indices of elements that must come after element j
    val dependents = List(n) { mutableListOf<Int>() }
    val unmetCount = IntArray(n)
    for (i in 0 until n) {
        for (j in 0 until n) {
            if (i != j && comparator.compare(elements[i], elements[j]) > 0) {
                dependents[j].add(i)
                unmetCount[i]++
            }
        }
    }

    val ready = ArrayDeque<Int>()
    for (i in 0 until n) if (unmetCount[i] == 0) ready.addLast(i)

    val sortedElements = mutableListOf<E>()
    while (ready.isNotEmpty()) {
        val j = ready.removeFirst()
        sortedElements.add(elements[j])
        for (i in dependents[j]) {
            unmetCount[i]--
            if (unmetCount[i] == 0) ready.addLast(i)
        }
    }

    if (sortedElements.size == n) {
        return sortedElements
    } else {
        val unsorted = (0 until n).filter { unmetCount[it] > 0 }.map { elements[it] }
        error("Could not sort elements, the ordering contains a cycle involving:\n${unsorted.joinToString("\n")}")
    }
}