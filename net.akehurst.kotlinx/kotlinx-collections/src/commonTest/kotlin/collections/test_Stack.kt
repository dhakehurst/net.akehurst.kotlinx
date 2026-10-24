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

    @Test
    fun many_pops_do_not_overflow() {
        // each pop used to wrap the list in another subList; on JS that overflowed the call stack
        val n = 20000
        var s = Stack(List(n) { it })
        repeat(n - 1) { s = s.pop().stack }
        assertEquals(1, s.size)
        assertEquals(0, s.peek())
        assertEquals(listOf(0), s.elements)
    }

    @Test
    fun many_pushes() {
        val n = 20000
        var s = Stack<Int>()
        repeat(n) { s = s.push(it) }
        assertEquals(n, s.size)
        assertEquals(n - 1, s.peek())
        assertEquals(List(n) { it }, s.elements)
    }

    @Test
    fun stacks_sharing_lower_elements_are_independent() {
        val base = Stack(listOf(1, 2))
        val a = base.push(3)
        val b = base.push(4)
        assertEquals(listOf(1, 2, 3), a.elements)
        assertEquals(listOf(1, 2, 4), b.elements)
        assertEquals(listOf(1, 2), base.elements)
        assertNotEquals(a, b)
        assertEquals(a.pop().stack, b.pop().stack)
    }

    @Test
    fun constructor_copies_the_given_list() {
        val source = mutableListOf(1, 2)
        val s = Stack(source)
        source.add(3)
        assertEquals(listOf(1, 2), s.elements)
        assertEquals(2, s.peek())
    }

    @Test
    fun null_items_are_allowed() {
        val s = Stack<String?>().push("a").push(null)
        assertNull(s.peek())
        assertNull(s.peekOrNull())
        val r = s.pop()
        assertNull(r.item)
        assertEquals("a", r.stack.peek())
    }

    @Test
    fun toString_shows_elements() {
        assertEquals("Stack[1, 2]", Stack(listOf(1, 2)).toString())
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
        assertEquals(listOf(2, 3), s.peek(2))
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
        assertEquals(listOf(2, 3), peeked)
    }

    @Test
    fun peek_n_result_is_not_affected_by_later_pushes() {
        val s = mutableStackOf(1, 2, 3)
        val peeked = s.peek(2)
        s.push(4)
        assertEquals(listOf(2, 3), peeked)
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
        assertEquals(listOf(2, 3), popped)
        assertEquals(listOf(1), s.elements)
    }

    @Test
    fun pop_n_returns_the_same_list_as_peek_n() {
        val s = mutableStackOf("a", "b", "c", "d")
        val peeked = s.peek(3)
        assertEquals(peeked, s.pop(3))
        assertEquals(listOf("b", "c", "d"), peeked)
        assertEquals(listOf("a"), s.elements)
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

class test_MutableStackElements {

    @Test
    fun elements_is_a_copy_not_affected_by_later_changes() {
        val s = mutableStackOf(1, 2)
        val e = s.elements
        s.push(3)
        s.pop()
        s.pop()
        assertEquals(listOf(1, 2), e)
        assertEquals(listOf(1), s.elements)
    }

    @Test
    fun changing_a_cast_elements_list_does_not_change_the_stack() {
        val s = mutableStackOf(1, 2)
        val e = s.elements
        if (e is MutableList<Int>) {
            e.add(99)
        }
        assertEquals(listOf(1, 2), s.elements)
        assertEquals(2, s.peek())
    }
}
