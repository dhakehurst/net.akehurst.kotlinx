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

import net.akehurst.kotlinx.utils.MutableReferenceDefault
import net.akehurst.kotlinx.utils.Reference
import kotlin.test.*

/**
 * Managed collections create their storage on the first add.
 * These tests check that an empty collection (no storage yet) and the first add behave as before.
 */
class test_ManagedCollectionsEmpty {

    private val added = mutableListOf<Any>()
    private val removed = mutableListOf<Any>()
    private val onAdded: (Any) -> Unit = { added.add(it) }
    private val onRemoved: (Any) -> Unit = { removed.add(it) }

    private fun all(): List<MutableCollection<Any>> = listOf(
        ManagedCollection("c", String::class, onAdded, onRemoved),
        ManagedList("l", String::class, onAdded, onRemoved),
        ManagedSet("s", String::class, onAdded, onRemoved),
        ManagedOrderedSet("os", String::class, onAdded, onRemoved),
    )

    private fun noHooksFired() {
        assertTrue(added.isEmpty(), "onAdded fired: $added")
        assertTrue(removed.isEmpty(), "onRemoved fired: $removed")
    }

    @Test
    fun empty_size_isEmpty_contains() {
        for (c in all()) {
            assertEquals(0, c.size, c::class.simpleName)
            assertTrue(c.isEmpty())
            assertFalse(c.contains("a"))
            assertFalse(c.containsAll(listOf("a")))
            assertTrue(c.containsAll(emptyList()))
            assertEquals(emptyList(), c.toList())
        }
        noHooksFired()
    }

    @Test
    fun empty_iterator() {
        for (c in all()) {
            val it = c.iterator()
            assertFalse(it.hasNext())
            assertFailsWith<NoSuchElementException> { it.next() }
            assertFailsWith<IllegalStateException> { it.remove() }
        }
        noHooksFired()
    }

    @Test
    fun empty_remove_clear_retain() {
        for (c in all()) {
            assertFalse(c.remove("a"))
            assertFalse(c.removeAll(listOf("a")))
            assertFalse(c.retainAll(listOf("a")))
            c.clear()
            assertTrue(c.isEmpty())
        }
        noHooksFired()
    }

    @Test
    fun empty_equals_and_hashCode() {
        val l = ManagedList<Any>("l", String::class)
        assertEquals<List<Any>>(emptyList(), l)
        assertEquals(emptyList<Any>().hashCode(), l.hashCode())
        val s = ManagedSet<Any>("s", String::class)
        assertEquals<Set<Any>>(emptySet(), s)
        assertEquals(emptySet<Any>().hashCode(), s.hashCode())
        val os = ManagedOrderedSet<Any>("os", String::class)
        assertEquals<Set<Any>>(emptySet(), os)
        assertEquals(ManagedOrderedSet<Any>("os2", String::class), os)
        assertEquals(emptySet<Any>().hashCode(), os.hashCode())
    }

    @Test
    fun empty_list_index_access_fails() {
        val l = ManagedList("l", String::class, onAdded, onRemoved)
        assertFailsWith<IndexOutOfBoundsException> { l[0] }
        assertFailsWith<IndexOutOfBoundsException> { l[-1] }
        assertFailsWith<IndexOutOfBoundsException> { l[0] = "a" }
        assertFailsWith<IndexOutOfBoundsException> { l.removeAt(0) }
        assertFailsWith<IndexOutOfBoundsException> { l.add(1, "a") }
        assertEquals(-1, l.indexOf("a"))
        assertEquals(-1, l.lastIndexOf("a"))
        assertFalse(l.listIterator().hasNext())
        assertFalse(l.listIterator().hasPrevious())
        assertEquals(emptyList(), l.subList(0, 0))
        assertTrue(l.isEmpty())
        noHooksFired()
    }

    @Test
    fun empty_orderedSet_index_access_fails() {
        val s = ManagedOrderedSet("os", String::class, onAdded, onRemoved)
        assertFailsWith<IndexOutOfBoundsException> { s[0] }
        assertFailsWith<IndexOutOfBoundsException> { s[-1] }
        noHooksFired()
    }

    @Test
    fun empty_wrong_type_add_fails_and_stays_empty() {
        for (c in all()) {
            assertFailsWith<IllegalArgumentException> { c.add(1) }
            assertTrue(c.isEmpty())
            assertFalse(c.iterator().hasNext())
        }
        val l = ManagedList("l", String::class, onAdded, onRemoved)
        assertFailsWith<IllegalArgumentException> { l.add(0, 1) }
        assertTrue(l.isEmpty())
        noHooksFired()
    }

    @Test
    fun first_add_after_empty() {
        for (c in all()) {
            added.clear()
            assertTrue(c.add("a"))
            assertEquals(1, c.size)
            assertTrue(c.contains("a"))
            assertEquals(listOf<Any>("a"), c.toList())
            assertEquals(listOf<Any>("a"), added)
        }
    }

    @Test
    fun first_add_at_index_on_empty_list() {
        val l = ManagedList("l", String::class, onAdded, onRemoved)
        l.add(0, "b")
        l.add(0, "a")
        assertEquals(listOf<Any>("a", "b"), l)
        assertEquals("a", l[0])
        assertEquals(listOf<Any>("b", "a"), added)
    }

    @Test
    fun first_addAll_on_empty() {
        for (c in all()) {
            added.clear()
            assertTrue(c.addAll(listOf("c", "a", "b")))
            assertEquals(3, c.size)
            assertEquals(listOf<Any>("c", "a", "b"), added)
        }
        val os = ManagedOrderedSet<Any>("os", String::class)
        os.addAll(listOf("c", "a", "b", "a"))
        assertEquals(listOf<Any>("c", "a", "b"), os.toList())
        assertEquals("c", os[0])
        assertEquals("b", os[2])
    }

    @Test
    fun emptied_again_then_add() {
        for (c in all()) {
            added.clear()
            removed.clear()
            c.add("a")
            c.remove("a")
            assertTrue(c.isEmpty())
            assertFalse(c.iterator().hasNext())
            assertEquals(listOf<Any>("a"), removed)
            c.add("b")
            assertEquals(listOf<Any>("b"), c.toList())
            assertEquals(listOf<Any>("a", "b"), added)
        }
    }

    @Test
    fun empty_reference_collections() {
        val colls: List<MutableCollection<Reference<Any, String>>> = listOf(
            ManagedReferenceCollection("rc", String::class),
            ManagedReferenceList("rl", String::class),
            ManagedReferenceSet("rs", String::class),
            ManagedReferenceOrderedSet("ros", String::class),
        )
        for (c in colls) {
            assertTrue(c.isEmpty())
            assertFalse(c.iterator().hasNext())
            val r = MutableReferenceDefault<Any, String>("k")
            assertFalse(c.remove(r))
            assertTrue(c.add(r))
            assertEquals(listOf<Reference<Any, String>>(r), c.toList())
        }
    }

    @Test
    fun reference_collection_messages_still_use_the_name() {
        @Suppress("UNCHECKED_CAST")
        val bad = MutableReferenceDefault<Any, Any>("k").also { it.resolved = 1 } as Reference<Any, String>
        val msg = assertFailsWith<IllegalArgumentException> { ManagedReferenceList("myList", String::class).add(bad) }.message ?: ""
        assertTrue(msg.contains("'myList'"), msg)
    }
}
