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

package net.akehurst.kotlinx.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class test_ManagedReferenceAndValue {

    @Test
    fun managedReference_type_violation_message() {
        val r = ManagedReference<String, Any>("key", "r", String::class)
        val e = assertFailsWith<IllegalArgumentException> { r.set("key", 42) }
        assertEquals("Managed Reference violation on 'r': Expected reference to 'String', but got 'Int'.", e.message)
    }

    @Test
    fun managedValue_type_violation_message() {
        val v = ManagedValue<Any>("a", "v", String::class)
        val e = assertFailsWith<IllegalArgumentException> { v.set(42) }
        assertEquals("Managed Value violation on 'v': Expected 'String', but got 'Int'.", e.message)
    }

    @Test
    fun managedReference_clear_when_unresolved_clears_reference() {
        val r = ManagedReference<String, Any>("key", "r", String::class)
        r.clear()
        assertEquals(null, r.reference)
    }

    @Test
    fun managedReference_set_new_reference_when_unresolved() {
        var changes = 0
        val r = ManagedReference<String, Any>("a", "r", String::class, onChanged = { _, _ -> changes++ })
        r.set("b", null)
        assertEquals("b", r.reference)
        assertEquals(1, changes)
    }

    @Test
    fun managedReference_set_same_reference_and_value_does_nothing() {
        var changes = 0
        val r = ManagedReference<String, Any>("a", "r", String::class, onChanged = { _, _ -> changes++ })
        r.set("a", null)
        assertEquals(0, changes)
    }

    // --- ManagedValue ---

    @Test
    fun managedValue_initial_value_is_checked() {
        assertFailsWith<IllegalArgumentException> { ManagedValue<Any>(42, "v", String::class) }
    }

    @Test
    fun managedValue_null_is_allowed() {
        val log = mutableListOf<String>()
        val v = ManagedValue<String?>(null, "v", String::class) { old, new -> log += "$old -> $new" }
        v.set("a")
        v.set(null)
        assertNull(v.get())
        assertEquals(listOf("null -> a", "a -> null"), log)
    }

    @Test
    fun managedValue_assigning_value_is_checked_and_reported() {
        val log = mutableListOf<String>()
        val v = ManagedValue<Any>("a", "v", String::class) { old, new -> log += "$old -> $new" }
        v.value = "b"
        assertFailsWith<IllegalArgumentException> { v.value = 42 }
        assertEquals("b", v.get())
        assertEquals(listOf("a -> b"), log)
    }

    @Test
    fun managedValue_equality_is_identity() {
        val a = ManagedValue<Any>("x", "a", String::class)
        val b = ManagedValue<Any>("x", "a", String::class)
        assertEquals(a, a)
        assertNotEquals(a, b)
    }

    // --- ManagedReference: assigning the key ---

    @Test
    fun managedReference_assigning_new_key_clears_resolved_and_reports() {
        val changes = mutableListOf<String>()
        val r = ManagedReference<String, Any>(null, "r", String::class) { old, new ->
            changes += "${old.reference}:${old.resolved} -> ${new.reference}:${new.resolved}"
        }
        r.set("k1", "V1")
        r.reference = "k2"
        assertEquals("k2", r.reference)
        assertNull(r.resolved)
        assertEquals(listOf("null:null -> k1:V1", "k1:V1 -> k2:null"), changes)
    }

    @Test
    fun managedReference_assigning_same_key_keeps_resolved() {
        var changes = 0
        val r = ManagedReference<String, Any>(null, "r", String::class) { _, _ -> changes++ }
        r.set("k1", "V1")
        r.reference = "k1"
        assertEquals("V1", r.resolved)
        assertEquals(1, changes)
    }

    // --- Reference equality: by key, across all Reference classes ---

    @Test
    fun references_with_same_key_are_equal_whatever_their_class() {
        val managed = ManagedReference<String, Any>("k", "r", String::class)
        val default = MutableReferenceDefault<String, Any>("k")
        val immutable = ReferenceImmutable<String, Any>("k", "V")
        val all = listOf<Any>(managed, default, immutable)
        for (a in all) for (b in all) {
            assertEquals(a, b, "$a should equal $b")
            assertEquals(a.hashCode(), b.hashCode(), "hashCode of $a and $b")
        }
        assertNotEquals<Any>(managed, MutableReferenceDefault<String, Any>("other"))
    }

    @Test
    fun list_remove_with_a_ReferenceImmutable_removes_the_managed_reference() {
        val managed = ManagedReference<String, Any>("k", "r", String::class)
        val list = mutableListOf<Reference<String, Any>>(managed)
        assertTrue(list.remove(ReferenceImmutable("k", null)))
        assertTrue(list.isEmpty())
    }
}
