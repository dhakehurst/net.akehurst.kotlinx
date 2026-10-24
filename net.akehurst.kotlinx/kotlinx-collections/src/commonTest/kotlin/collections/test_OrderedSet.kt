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

class test_OrderedSet {

    private fun assertSymmetricEquality(a: Any, b: Any) {
        assertEquals(a == b, b == a, "equals must be symmetric: ($a == $b) is ${a == b}, ($b == $a) is ${b == a}")
        if (a == b) {
            assertEquals(a.hashCode(), b.hashCode(), "equal objects must have equal hashCodes: $a, $b")
        }
    }

    // --- construction ---

    @Test
    fun orderedSetOf_keeps_insertion_order_and_removes_duplicates() {
        val s = orderedSetOf(3, 1, 2, 1)
        assertEquals(3, s.size)
        assertEquals(listOf(3, 1, 2), s.toList())
    }

    @Test
    fun get_by_index() {
        val s = orderedSetOf("c", "a", "b")
        assertEquals("c", s[0])
        assertEquals("a", s[1])
        assertEquals("b", s[2])
    }

    @Test
    fun get_out_of_bounds_fails() {
        val s = orderedSetOf(1, 2, 3)
        assertFailsWith<IndexOutOfBoundsException> { s[3] }
        assertFailsWith<IndexOutOfBoundsException> { s[-1] }
    }

    @Test
    fun contains_and_containsAll() {
        val s = orderedSetOf(1, 2, 3)
        assertTrue(s.contains(2))
        assertFalse(s.contains(4))
        assertTrue(s.containsAll(listOf(3, 1)))
        assertFalse(s.containsAll(listOf(1, 4)))
    }

    @Test
    fun toString_lists_elements_in_order() {
        assertEquals("[2, 1]", orderedSetOf(2, 1).toString())
    }

    @Test
    fun toOrderedSet_from_collection() {
        assertEquals(listOf(2, 1, 3), listOf(2, 1, 2, 3).toOrderedSet().toList())
    }

    @Test
    fun toOrderedSet_from_non_collection_iterable() {
        val iterable = Iterable { listOf(2, 1, 2, 3).iterator() }
        assertEquals(listOf(2, 1, 3), iterable.toOrderedSet().toList())
    }

    @Test
    fun toMutableOrderedSet_is_a_copy() {
        val source = mutableListOf(1, 2)
        val s = source.toMutableOrderedSet()
        source.add(3)
        assertEquals(listOf(1, 2), s.toList())
    }

    // --- empty ---

    @Test
    fun emptyOrderedSet_is_empty() {
        val s = emptyOrderedSet<Int>()
        assertEquals(0, s.size)
        assertTrue(s.isEmpty())
        assertFalse(s.iterator().hasNext())
        assertFalse(s.contains(1))
        assertTrue(s.containsAll(emptyList()))
        assertFailsWith<IndexOutOfBoundsException> { s[0] }
        assertEquals("[]", s.toString())
    }

    @Test
    fun emptyOrderedSet_equals_other_empty_ordered_sets() {
        assertEquals(emptyOrderedSet<Int>(), orderedSetOf<Int>())
        assertEquals(orderedSetOf<Int>(), emptyOrderedSet<Int>())
        assertEquals(emptyOrderedSet<Int>(), mutableOrderedSetOf<Int>())
    }

    // --- mutable ---

    @Test
    fun mutableOrderedSet_add_keeps_order_and_rejects_duplicates() {
        val s = mutableOrderedSetOf(1, 2)
        assertTrue(s.add(3))
        assertFalse(s.add(1))
        assertEquals(listOf(1, 2, 3), s.toList())
        assertEquals(3, s[2])
    }

    @Test
    fun mutableOrderedSet_remove_keeps_order_of_rest() {
        val s = mutableOrderedSetOf(1, 2, 3, 4)
        assertTrue(s.remove(2))
        assertFalse(s.remove(9))
        assertEquals(listOf(1, 3, 4), s.toList())
        assertEquals(3, s[1])
    }

    @Test
    fun mutableOrderedSet_addAll_removeAll_retainAll_clear() {
        val s = mutableOrderedSetOf(1, 2)
        assertTrue(s.addAll(listOf(2, 3, 4)))
        assertEquals(listOf(1, 2, 3, 4), s.toList())
        assertTrue(s.removeAll(listOf(1, 3)))
        assertEquals(listOf(2, 4), s.toList())
        assertTrue(s.retainAll(listOf(4)))
        assertEquals(listOf(4), s.toList())
        s.clear()
        assertTrue(s.isEmpty())
    }

    @Test
    fun mutableOrderedSet_iterator_remove() {
        val s = mutableOrderedSetOf(1, 2, 3)
        val it = s.iterator()
        it.next()
        it.remove()
        assertEquals(listOf(2, 3), s.toList())
    }

    // --- operators ---

    @Test
    fun plus_element_appends() {
        val s = orderedSetOf(1, 2)
        assertEquals(listOf(1, 2, 3), (s + 3).toList())
        // original unchanged
        assertEquals(listOf(1, 2), s.toList())
    }

    @Test
    fun plus_existing_element_does_not_duplicate() {
        assertEquals(listOf(1, 2), (orderedSetOf(1, 2) + 1).toList())
    }

    @Test
    fun plus_iterable_appends_new_elements_in_order() {
        val s = orderedSetOf(1, 2)
        assertEquals(listOf(1, 2, 3, 4), (s + listOf(2, 3, 4)).toList())
        assertEquals(listOf(1, 2), s.toList())
    }

    @Test
    fun plusAssign_adds_elements() {
        val s = mutableOrderedSetOf(1)
        s += listOf(2, 1, 3)
        assertEquals(listOf(1, 2, 3), s.toList())
    }

    // --- equality ---

    @Test
    fun ordered_sets_with_same_elements_in_same_order_are_equal() {
        val a = orderedSetOf(1, 2, 3)
        val b = mutableOrderedSetOf(1, 2, 3)
        assertEquals(a, b)
        assertEquals(b, a)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun ordered_sets_with_different_elements_are_not_equal() {
        assertNotEquals(orderedSetOf(1, 2), orderedSetOf(1, 3))
        assertNotEquals(orderedSetOf(1, 2), orderedSetOf(1, 2, 3))
    }

    @Test
    fun equality_with_plain_set_is_symmetric() {
        assertSymmetricEquality(orderedSetOf(1, 2), setOf(1, 2))
        assertSymmetricEquality(orderedSetOf(1, 2), setOf(2, 1))
        assertSymmetricEquality(mutableOrderedSetOf(1, 2), mutableSetOf(2, 1))
        assertSymmetricEquality(emptyOrderedSet<Int>(), emptySet<Int>())
    }

    @Test
    fun equality_between_ordered_set_implementations_is_symmetric() {
        val managed = ManagedOrderedSet<Int>("m", Int::class)
        managed.add(2)
        managed.add(1)
        assertSymmetricEquality(orderedSetOf(1, 2), managed)
        assertSymmetricEquality(orderedSetOf(2, 1), managed)
        assertSymmetricEquality(mutableOrderedSetOf(1, 2), managed)
    }
}
