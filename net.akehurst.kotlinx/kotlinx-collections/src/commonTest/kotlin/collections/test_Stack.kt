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

class test_Stack {

    @Test
    fun empty_stack() {
        val s = Stack<Int>()
        assertTrue(s.isEmpty)
        assertFalse(s.isNotEmpty)
        assertEquals(0, s.size)
        assertNull(s.peekOrNull())
        assertFailsWith<NoSuchElementException> { s.peek() }
    }

    @Test
    fun pop_empty_fails() {
        assertFails { Stack<Int>().pop() }
    }

    @Test
    fun push_returns_new_stack_and_leaves_original_unchanged() {
        val s0 = Stack<Int>()
        val s1 = s0.push(1)
        val s2 = s1.push(2)

        assertEquals(0, s0.size)
        assertEquals(listOf(1), s1.elements)
        assertEquals(listOf(1, 2), s2.elements)
        assertEquals(2, s2.peek())
        assertEquals(2, s2.peekOrNull())
    }

    @Test
    fun pushAll_puts_last_item_on_top() {
        val s = Stack<Int>().push(1).pushAll(listOf(2, 3))
        assertEquals(listOf(1, 2, 3), s.elements)
        assertEquals(3, s.peek())
    }

    @Test
    fun pop_returns_top_and_remaining_stack() {
        val s = Stack(listOf(1, 2, 3))
        val r = s.pop()
        assertEquals(3, r.item)
        assertEquals(listOf(1, 2), r.stack.elements)
        // original unchanged
        assertEquals(listOf(1, 2, 3), s.elements)
    }

    @Test
    fun pop_until_empty() {
        var s = Stack(listOf(1, 2))
        s = s.pop().stack
        s = s.pop().stack
        assertTrue(s.isEmpty)
    }

    @Test
    fun equals_and_hashCode_by_elements() {
        val a = Stack(listOf(1, 2))
        val b = Stack<Int>().push(1).push(2)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, Stack(listOf(2, 1)))
        assertNotEquals<Any>(a, listOf(1, 2))
    }

    @Test
    fun clone_is_equal() {
        val a = Stack(listOf(1, 2))
        assertEquals(a, a.clone())
    }
}

class test_MutableStack {

    @Test
    fun mutableStackOf_pushes_in_order() {
        val s = mutableStackOf(1, 2, 3)
        assertEquals(3, s.size)
        assertEquals(3, s.peek())
        assertEquals(listOf(1, 2, 3), s.elements)
    }

    @Test
    fun empty_stack() {
        val s = MutableStack<Int>()
        assertTrue(s.isEmpty)
        assertFalse(s.isNotEmpty)
        assertNull(s.peekOrNull())
        assertFails { s.peek() }
        assertFails { s.pop() }
    }

    @Test
    fun push_pop_is_lifo() {
        val s = MutableStack<String>()
        s.push("a")
        s.push("b")
        s.push("c")
        assertEquals("c", s.pop())
        assertEquals("b", s.pop())
        assertEquals("a", s.pop())
        assertTrue(s.isEmpty)
    }

    @Test
    fun clear_empties_stack() {
        val s = mutableStackOf(1, 2)
        s.clear()
        assertTrue(s.isEmpty)
        assertEquals(0, s.size)
    }

    @Test
    fun peek_n_returns_top_n_without_removing() {
        val s = mutableStackOf(1, 2, 3)
        // order of the returned list is not asserted (see open question on peek(n)/pop(n) ordering)
        assertEquals(setOf(2, 3), s.peek(2).toSet())
        assertEquals(3, s.size)
    }

    @Test
    fun peek_n_zero_is_empty() {
        val s = mutableStackOf(1, 2, 3)
        assertTrue(s.peek(0).isEmpty())
    }

    @Test
    fun peek_n_result_is_not_affected_by_later_pops() {
        val s = mutableStackOf(1, 2, 3)
        val peeked = s.peek(2)
        s.pop()
        s.pop()
        assertEquals(setOf(2, 3), peeked.toSet())
    }

    @Test
    fun peek_n_result_is_not_affected_by_later_pushes() {
        val s = mutableStackOf(1, 2, 3)
        val peeked = s.peek(2)
        s.push(4)
        assertEquals(setOf(2, 3), peeked.toSet())
    }

    @Test
    fun peek_n_more_than_size_fails() {
        val s = mutableStackOf(1, 2)
        assertFails { s.peek(3) }
    }

    @Test
    fun pop_n_removes_top_n() {
        val s = mutableStackOf(1, 2, 3)
        val popped = s.pop(2)
        // order of the returned list is not asserted (see open question on peek(n)/pop(n) ordering)
        assertEquals(setOf(2, 3), popped.toSet())
        assertEquals(listOf(1), s.elements)
    }

    @Test
    fun pop_n_zero_removes_nothing() {
        val s = mutableStackOf(1, 2, 3)
        assertTrue(s.pop(0).isEmpty())
        assertEquals(3, s.size)
    }

    @Test
    fun pop_n_more_than_size_fails_without_changing_stack() {
        val s = mutableStackOf(1, 2)
        assertFails { s.pop(3) }
        assertEquals(listOf(1, 2), s.elements)
    }
}
