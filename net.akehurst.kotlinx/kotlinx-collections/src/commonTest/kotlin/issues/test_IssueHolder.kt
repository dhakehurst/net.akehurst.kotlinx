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

package net.akehurst.kotlinx.issues.ram

import net.akehurst.kotlinx.issues.api.IssueKind
import kotlin.test.*

class test_IssueHolder {

    @Test
    fun new_holder_is_empty() {
        val h = IssueHolder<String>()
        assertTrue(h.isEmpty())
        assertEquals(0, h.size)
        assertTrue(h.all.isEmpty())
        assertTrue(h.errors.isEmpty())
        assertTrue(h.warnings.isEmpty())
        assertTrue(h.informations.isEmpty())
        assertEquals("", h.toString())
    }

    @Test
    fun constructed_with_initial_issues() {
        val i = IssueDefault(IssueKind.ERROR, "loc", "msg")
        val h = IssueHolder(listOf(i))
        assertEquals(1, h.size)
        assertTrue(h.contains(i))
    }

    @Test
    fun raise_helpers_create_issues_of_the_right_kind() {
        val h = IssueHolder<String>()
        h.info("l1", "i")
        h.warn("l2", "w")
        h.error("l3", "e", data = 7)
        h.raise(IssueKind.WARNING, null, "w2")

        assertEquals(4, h.size)
        assertEquals(listOf(IssueDefault(IssueKind.INFORMATION, "l1", "i")), h.informations)
        assertEquals(listOf(IssueDefault(IssueKind.WARNING, "l2", "w"), IssueDefault<String>(IssueKind.WARNING, null, "w2")), h.warnings)
        assertEquals(listOf(IssueDefault(IssueKind.ERROR, "l3", "e", 7)), h.errors)
    }

    @Test
    fun issue_properties() {
        val i = IssueDefault(IssueKind.ERROR, "loc", "msg", data = listOf(1))
        assertEquals(IssueKind.ERROR, i.kind)
        assertEquals("loc", i.location)
        assertEquals("msg", i.message)
        assertEquals(listOf(1), i.data)
    }

    @Test
    fun identical_issues_are_held_once() {
        val h = IssueHolder<String>()
        h.error("l", "e")
        h.error("l", "e")
        assertEquals(1, h.size)
        assertEquals(1, h.errors.size)
    }

    @Test
    fun issues_differing_only_by_data_are_both_held() {
        val h = IssueHolder<String>()
        h.error("l", "e", 1)
        h.error("l", "e", 2)
        assertEquals(2, h.size)
    }

    @Test
    fun containsAll_and_iterator() {
        val h = IssueHolder<String>()
        h.info("a", "1")
        h.error("b", "2")
        val i1 = IssueDefault(IssueKind.INFORMATION, "a", "1")
        val i2 = IssueDefault(IssueKind.ERROR, "b", "2")
        assertTrue(h.containsAll(listOf(i1, i2)))
        assertEquals(listOf(i1, i2), h.iterator().asSequence().toList())
    }

    @Test
    fun clear_removes_all() {
        val h = IssueHolder<String>()
        h.error("l", "e")
        h.warn("l", "w")
        h.clear()
        assertTrue(h.isEmpty())
        assertTrue(h.errors.isEmpty())
    }

    @Test
    fun addAll_and_addAllFrom() {
        val a = IssueHolder<String>()
        a.error("a", "e")
        val b = IssueHolder<String>()
        b.addAll(listOf(IssueDefault(IssueKind.WARNING, "b", "w")))
        b.addAllFrom(a)
        assertEquals(2, b.size)
        assertEquals(1, b.errors.size)
        assertEquals(1, b.warnings.size)
        // source unchanged
        assertEquals(1, a.size)
    }

    @Test
    fun plus_combines_without_changing_operands() {
        val a = IssueHolder<String>()
        a.error("a", "e")
        val b = IssueHolder<String>()
        b.warn("b", "w")
        b.error("a", "e") // same as in a

        val c = a + b
        assertEquals(2, c.size)
        assertEquals(1, a.size)
        assertEquals(2, b.size)
    }

    @Test
    fun toString_lists_one_issue_per_line_in_insertion_order() {
        val h = IssueHolder<String>()
        h.error("a", "e")
        h.warn("b", "w")
        val expected = "${IssueDefault(IssueKind.ERROR, "a", "e")}\n${IssueDefault(IssueKind.WARNING, "b", "w")}"
        assertEquals(expected, h.toString())
    }
}
