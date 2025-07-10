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
 */
fun <E> Collection<E>.topologicalSort(comparator: Comparator<in E>): List<E> {
    //TODO: perhaps can find something faster!

    val comparisons = this.associate { el1 ->
        val set = this.filter { el2 ->
            el2 !== el1 && 1 == comparator.compare(el1, el2) //de.dependsOn(it)
        }.toMutableSet()
        Pair(el1, set)
    }.toMutableMap()

    val toRemove = mutableSetOf<E>()
    val sortedElements = mutableListOf<E>()
    val S = comparisons.filter {
        it.value.isEmpty()
    }.map { it.key }.toMutableList()
    S.forEach { comparisons.remove(it) }

    while (S.isNotEmpty()) {
        val n = S.first()
        S -= n
        sortedElements.add(n)
        for (comp in comparisons) {
            val set = comp.value
            if (set.contains(n)) {
                set.remove(n)
                if (set.isEmpty()) {
                    S += comp.key
                    toRemove.add(comp.key)
                } else {
                    // other relationship
                }
            } else {
                // no comparator relationship
            }
        }
        if (toRemove.isNotEmpty()) {
            toRemove.forEach { comparisons.remove(it) }
            toRemove.clear()
        }
    }
    if (comparisons.isEmpty()) {
        return sortedElements
    } else {
        error("Internal Error: Could not sort elements ")
    }
}