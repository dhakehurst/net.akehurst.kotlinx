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

import kotlin.test.*

class test_topologicalSort {

    /** compare(a, b) > 0 means 'a' depends on 'b', so 'b' must come first; 0 means unrelated */
    private fun dependsOn(deps: Map<String, Set<String>>) = object : Comparator<String> {
        override fun compare(a: String, b: String): Int = when {
            deps[a]?.contains(b) == true -> 1
            deps[b]?.contains(a) == true -> -1
            else -> 0
        }
    }

    private fun assertBefore(list: List<String>, first: String, second: String) {
        assertTrue(list.indexOf(first) < list.indexOf(second), "expected '$first' before '$second' in $list")
    }

    @Test
    fun empty_collection() {
        assertEquals(emptyList(), emptyList<String>().topologicalSort(dependsOn(emptyMap())))
    }

    @Test
    fun single_element() {
        assertEquals(listOf("a"), listOf("a").topologicalSort(dependsOn(emptyMap())))
    }

    @Test
    fun total_order_with_natural_comparator() {
        assertEquals(listOf(1, 2, 3, 4), listOf(3, 1, 4, 2).topologicalSort(naturalOrder()))
    }

    @Test
    fun total_order_with_comparator_returning_values_other_than_1() {
        // Comparator contract only guarantees a positive / negative / zero result, not exactly 1 / -1
        val byDifference = object : Comparator<Int> {
            override fun compare(a: Int, b: Int): Int = a - b
        }
        assertEquals(listOf(10, 20, 30), listOf(30, 10, 20).topologicalSort(byDifference))
    }

    @Test
    fun dependency_chain() {
        val deps = mapOf("c" to setOf("b"), "b" to setOf("a"))
        val result = listOf("c", "b", "a").topologicalSort(dependsOn(deps))
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun partial_order_keeps_unrelated_elements() {
        val deps = mapOf("c" to setOf("b"), "b" to setOf("a"))
        val result = listOf("c", "d", "a", "b").topologicalSort(dependsOn(deps))
        assertEquals(setOf("a", "b", "c", "d"), result.toSet())
        assertEquals(4, result.size)
        assertBefore(result, "a", "b")
        assertBefore(result, "b", "c")
    }

    @Test
    fun diamond_dependencies() {
        // d depends on b and c, both depend on a
        val deps = mapOf("d" to setOf("b", "c"), "b" to setOf("a"), "c" to setOf("a"))
        val result = listOf("d", "c", "b", "a").topologicalSort(dependsOn(deps))
        assertEquals(4, result.size)
        assertBefore(result, "a", "b")
        assertBefore(result, "a", "c")
        assertBefore(result, "b", "d")
        assertBefore(result, "c", "d")
    }

    @Test
    fun all_unrelated_elements_are_all_returned() {
        val result = listOf("x", "y", "z").topologicalSort(dependsOn(emptyMap()))
        assertEquals(setOf("x", "y", "z"), result.toSet())
        assertEquals(3, result.size)
    }

    @Test
    fun equal_elements_are_not_lost() {
        val result = listOf(2, 1, 2, 1).topologicalSort(naturalOrder())
        assertEquals(listOf(1, 1, 2, 2), result)
    }

    @Test
    fun cycle_fails() {
        val deps = mapOf("a" to setOf("b"), "b" to setOf("a"))
        assertFails { listOf("a", "b").topologicalSort(dependsOn(deps)) }
    }

    @Test
    fun does_not_modify_input() {
        val input = listOf(3, 1, 2)
        input.topologicalSort(naturalOrder())
        assertEquals(listOf(3, 1, 2), input)
    }
}

class test_transitiveClosure {

    @Test
    fun chain() {
        val next = mapOf(1 to setOf(2), 2 to setOf(3), 3 to setOf(4))
        val result = setOf(1).transitveClosure { next[it] ?: emptySet() }
        assertEquals(setOf(1, 2, 3, 4), result)
    }

    @Test
    fun includes_start_elements_even_with_no_successors() {
        assertEquals(setOf(1, 2), setOf(1, 2).transitveClosure { emptySet() })
    }

    @Test
    fun empty_start_set() {
        assertEquals(emptySet(), emptySet<Int>().transitveClosure { setOf(it + 1) })
    }

    @Test
    fun cycle_terminates() {
        val next = mapOf(1 to setOf(2), 2 to setOf(3), 3 to setOf(1))
        assertEquals(setOf(1, 2, 3), setOf(1).transitveClosure { next[it] ?: emptySet() })
    }

    @Test
    fun branching() {
        val next = mapOf(1 to setOf(2, 3), 2 to setOf(4), 3 to setOf(4, 5))
        assertEquals(setOf(1, 2, 3, 4, 5), setOf(1).transitveClosure { next[it] ?: emptySet() })
    }

    @Test
    fun function_called_once_per_element() {
        val next = mapOf(1 to setOf(2, 3), 2 to setOf(3), 3 to setOf(1))
        val calls = mutableMapOf<Int, Int>()
        setOf(1).transitveClosure {
            calls[it] = (calls[it] ?: 0) + 1
            next[it] ?: emptySet()
        }
        assertEquals(mapOf(1 to 1, 2 to 1, 3 to 1), calls)
    }

    @Test
    fun does_not_modify_input() {
        val start = setOf(1)
        start.transitveClosure { if (it < 3) setOf(it + 1) else emptySet() }
        assertEquals(setOf(1), start)
    }
}
