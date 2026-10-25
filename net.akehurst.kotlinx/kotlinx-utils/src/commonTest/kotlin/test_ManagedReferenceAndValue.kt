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
}
