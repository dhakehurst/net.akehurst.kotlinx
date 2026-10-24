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

/** Records hook calls */
private class Hooks<E> {
    val added = mutableListOf<E>()
    val removed = mutableListOf<E>()
    val onAdded: (E) -> Unit = { added.add(it) }
    val onRemoved: (E) -> Unit = { removed.add(it) }
}

class test_ManagedCollection {

    private val hooks = Hooks<Any>()
    private val c = ManagedCollection<Any>("c", String::class, hooks.onAdded, hooks.onRemoved)

    @Test
    fun add_fires_onAdded() {
        assertTrue(c.add("a"))
        assertEquals(listOf<Any>("a"), hooks.added)
        assertTrue(c.contains("a"))
        assertEquals(1, c.size)
    }

    @Test
    fun allows_duplicates() {
        c.add("a")
        c.add("a")
        assertEquals(2, c.size)
        assertEquals(listOf<Any>("a", "a"), hooks.added)
    }

    @Test
    fun add_wrong_type_fails_and_does_not_add_or_fire() {
        assertFailsWith<IllegalArgumentException> { c.add(1) }
        assertTrue(c.isEmpty())
        assertTrue(hooks.added.isEmpty())
    }

    @Test
    fun addAll_validates_and_fires_for_each() {
        c.addAll(listOf("a", "b"))
        assertEquals(listOf<Any>("a", "b"), hooks.added)
    }

    @Test
    fun remove_fires_onRemoved() {
        c.add("a")
        c.add("b")
        assertTrue(c.remove("a"))
        assertEquals(listOf<Any>("a"), hooks.removed)
        assertFalse(c.contains("a"))
    }

    @Test
    fun remove_absent_element_does_not_fire() {
        c.add("a")
        assertFalse(c.remove("x"))
        assertTrue(hooks.removed.isEmpty())
    }

    @Test
    fun clear_fires_onRemoved_for_each() {
        c.add("a")
        c.add("b")
        c.clear()
        assertTrue(c.isEmpty())
        assertEquals(listOf<Any>("a", "b"), hooks.removed)
    }

    @Test
    fun iterator_remove_before_next_fails() {
        c.add("a")
        assertFailsWith<IllegalStateException> { c.iterator().remove() }
        assertEquals(1, c.size)
    }

    @Test
    fun iterator_remove_twice_fails() {
        c.add("a")
        c.add("b")
        val it = c.iterator()
        it.next()
        it.remove()
        assertFailsWith<IllegalStateException> { it.remove() }
        assertEquals(1, c.size)
    }

    @Test
    fun hooks_are_optional() {
        val noHooks = ManagedCollection<Any>("c", String::class)
        noHooks.add("a")
        noHooks.remove("a")
        assertTrue(noHooks.isEmpty())
    }

    @Test
    fun subtype_instances_are_accepted() {
        val numbers = ManagedCollection<Any>("n", Number::class)
        numbers.add(1)
        numbers.add(2.5)
        assertEquals(2, numbers.size)
        assertFailsWith<IllegalArgumentException> { numbers.add("x") }
    }
}

class test_ManagedList {

    private val hooks = Hooks<Any>()
    private val l = ManagedList<Any>("l", String::class, hooks.onAdded, hooks.onRemoved)

    @Test
    fun add_appends_and_fires() {
        l.add("a")
        l.add("b")
        assertEquals(listOf<Any>("a", "b"), l.toList())
        assertEquals("b", l[1])
        assertEquals(listOf<Any>("a", "b"), hooks.added)
    }

    @Test
    fun add_at_index_inserts_and_fires() {
        l.add("a")
        l.add("c")
        l.add(1, "b")
        assertEquals(listOf<Any>("a", "b", "c"), l.toList())
        assertEquals(listOf<Any>("a", "c", "b"), hooks.added)
    }

    @Test
    fun set_replaces_and_fires_removed_then_added() {
        l.add("a")
        assertEquals("a", l.set(0, "b"))
        assertEquals(listOf<Any>("b"), l.toList())
        assertEquals(listOf<Any>("a"), hooks.removed)
        assertEquals(listOf<Any>("a", "b"), hooks.added)
    }

    @Test
    fun removeAt_removes_and_fires() {
        l.add("a")
        l.add("b")
        assertEquals("a", l.removeAt(0))
        assertEquals(listOf<Any>("b"), l.toList())
        assertEquals(listOf<Any>("a"), hooks.removed)
    }

    @Test
    fun remove_element_fires() {
        l.add("a")
        l.add("b")
        assertTrue(l.remove("b"))
        assertEquals(listOf<Any>("b"), hooks.removed)
    }

    @Test
    fun clear_fires_for_each() {
        l.add("a")
        l.add("b")
        l.clear()
        assertTrue(l.isEmpty())
        assertEquals(setOf<Any>("a", "b"), hooks.removed.toSet())
        assertEquals(2, hooks.removed.size)
    }

    @Test
    fun addAll_fires_for_each() {
        l.addAll(listOf("a", "b"))
        assertEquals(listOf<Any>("a", "b"), l.toList())
        assertEquals(listOf<Any>("a", "b"), hooks.added)
    }

    @Test
    fun wrong_type_fails_on_add_insert_and_set() {
        l.add("a")
        assertFailsWith<IllegalArgumentException> { l.add(1) }
        assertFailsWith<IllegalArgumentException> { l.add(0, 1) }
        assertFailsWith<IllegalArgumentException> { l.set(0, 1) }
        assertEquals(listOf<Any>("a"), l.toList())
        assertEquals(listOf<Any>("a"), hooks.added)
        assertTrue(hooks.removed.isEmpty())
    }

    @Test
    fun get_out_of_bounds_fails() {
        assertFailsWith<IndexOutOfBoundsException> { l[0] }
    }
}

class test_ManagedSet {

    private val hooks = Hooks<Any>()
    private val s = ManagedSet<Any>("s", String::class, hooks.onAdded, hooks.onRemoved)

    @Test
    fun add_fires_once_and_rejects_duplicates() {
        assertTrue(s.add("a"))
        assertFalse(s.add("a"))
        assertEquals(1, s.size)
        assertEquals(listOf<Any>("a"), hooks.added)
    }

    @Test
    fun remove_fires() {
        s.add("a")
        assertTrue(s.remove("a"))
        assertFalse(s.remove("a"))
        assertEquals(listOf<Any>("a"), hooks.removed)
    }

    @Test
    fun clear_fires_for_each() {
        s.add("a")
        s.add("b")
        s.clear()
        assertTrue(s.isEmpty())
        assertEquals(setOf<Any>("a", "b"), hooks.removed.toSet())
    }

    @Test
    fun wrong_type_fails() {
        assertFailsWith<IllegalArgumentException> { s.add(1) }
        assertTrue(s.isEmpty())
        assertTrue(hooks.added.isEmpty())
    }

    @Test
    fun equals_plain_set_with_same_elements() {
        s.add("a")
        s.add("b")
        assertEquals<Set<Any>>(setOf("b", "a"), s)
        assertEquals<Set<Any>>(s, setOf("b", "a"))
    }
}

class test_ManagedOrderedSet {

    private val hooks = Hooks<Any>()
    private val s = ManagedOrderedSet<Any>("os", String::class, hooks.onAdded, hooks.onRemoved)

    @Test
    fun keeps_insertion_order_and_indexes() {
        s.add("c")
        s.add("a")
        s.add("b")
        assertEquals(listOf<Any>("c", "a", "b"), s.toList())
        assertEquals("c", s[0])
        assertEquals("b", s[2])
    }

    @Test
    fun get_out_of_bounds_fails() {
        s.add("a")
        assertFailsWith<IndexOutOfBoundsException> { s[1] }
        assertFailsWith<IndexOutOfBoundsException> { s[-1] }
    }

    @Test
    fun duplicates_rejected_without_firing() {
        s.add("a")
        assertFalse(s.add("a"))
        assertEquals(listOf<Any>("a"), hooks.added)
    }

    @Test
    fun remove_keeps_order_of_rest_and_fires() {
        s.add("a")
        s.add("b")
        s.add("c")
        assertTrue(s.remove("b"))
        assertEquals(listOf<Any>("a", "c"), s.toList())
        assertEquals("c", s[1])
        assertEquals(listOf<Any>("b"), hooks.removed)
    }

    @Test
    fun wrong_type_fails() {
        assertFailsWith<IllegalArgumentException> { s.add(1) }
        assertTrue(s.isEmpty())
    }
}

class test_ManagedReferenceCollections {

    /** creates a reference whose resolved value may be of a different type than the declared T */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> ref(key: String, resolved: Any?): Reference<Any, T> {
        val r = MutableReferenceDefault<Any, Any>(key)
        r.resolved = resolved
        return r as Reference<Any, T>
    }

    private fun <C : MutableCollection<Reference<Any, String>>> checkCommon(
        create: (onAdded: (Reference<Any, String>) -> Unit, onRemoved: (Reference<Any, String>) -> Unit) -> C
    ) {
        val added = mutableListOf<Reference<Any, String>>()
        val removed = mutableListOf<Reference<Any, String>>()
        val c = create({ added.add(it) }, { removed.add(it) })

        // resolved to the expected type
        val r1 = ref<String>("r1", "hello")
        assertTrue(c.add(r1))
        // unresolved references are accepted
        val r2 = ref<String>("r2", null)
        assertTrue(c.add(r2))
        assertEquals(listOf(r1, r2), added)

        // resolved to the wrong type
        assertFailsWith<IllegalArgumentException> { c.add(ref<String>("r3", 42)) }
        assertEquals(2, c.size)
        assertEquals(2, added.size)

        assertTrue(c.remove(r1))
        assertEquals(listOf(r1), removed)
    }

    @Test
    fun managedReferenceCollection() = checkCommon { a, r ->
        ManagedReferenceCollection("refs", String::class, a, r)
    }

    @Test
    fun managedReferenceList() = checkCommon { a, r ->
        ManagedReferenceList("refs", String::class, a, r)
    }

    @Test
    fun managedReferenceSet() = checkCommon { a, r ->
        ManagedReferenceSet("refs", String::class, a, r)
    }

    @Test
    fun managedReferenceOrderedSet() = checkCommon { a, r ->
        ManagedReferenceOrderedSet("refs", String::class, a, r)
    }

    @Test
    fun managedReferenceSet_rejects_reference_with_same_key() {
        val s = ManagedReferenceSet("refs", String::class)
        assertTrue(s.add(ref("k", "a")))
        // MutableReferenceDefault equality is by reference key
        assertFalse(s.add(ref("k", "b")))
        assertEquals(1, s.size)
    }

    @Test
    fun managedReferenceOrderedSet_keeps_order() {
        val s = ManagedReferenceOrderedSet("refs", String::class)
        val r1 = ref<String>("r1", "x")
        val r2 = ref<String>("r2", "y")
        s.add(r2)
        s.add(r1)
        assertSame(r2, s[0])
        assertSame(r1, s[1])
    }
}
