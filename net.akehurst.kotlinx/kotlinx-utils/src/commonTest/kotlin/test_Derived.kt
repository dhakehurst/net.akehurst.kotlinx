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
import kotlin.test.assertNull

class test_Derived {

    private class Person(var first: String, var last: String) {
        val fullNameDerived: Derived<String> = DerivedDefault { "$first $last" }
        val fullName: String get() = fullNameDerived.get()
    }

    @Test
    fun DerivedDefault_recomputes_on_each_get() {
        val p = Person("Ada", "Lovelace")
        assertEquals("Ada Lovelace", p.fullName)
        p.last = "Byron"
        assertEquals("Ada Byron", p.fullName)
    }

    @Test
    fun ManagedDerived_returns_value_of_expected_type() {
        val d: Derived<Any?> = ManagedDerived("Thing.size", Int::class) { 3 }
        assertEquals(3, d.get())
    }

    @Test
    fun ManagedDerived_allows_null() {
        val d: Derived<String?> = ManagedDerived("Thing.label", String::class) { null }
        assertNull(d.get())
    }

    @Test
    fun ManagedDerived_rejects_wrong_type() {
        val d: Derived<Any?> = ManagedDerived("Thing.label", String::class) { 42 }
        val e = assertFailsWith<IllegalStateException> { d.get() }
        assertEquals("Managed Derived violation on 'Thing.label': Expected 'String', but derivation gave 'Int'.", e.message)
    }

    private class Named(var declaredName: String?) {
        val nameDerived: MutableDerived<String?> = ManagedMutableDerived("Named.name", String::class, { declaredName }, { declaredName = it })
    }

    @Test
    fun ManagedMutableDerived_set_writes_through_inverse() {
        val n = Named("a")
        assertEquals("a", n.nameDerived.get())
        n.nameDerived.set("b")
        assertEquals("b", n.declaredName)
        assertEquals("b", n.nameDerived.get())
    }

    @Test
    fun mutable_ext_casts_settable_derived() {
        val n = Named("a")
        val d: Derived<String?> = n.nameDerived
        with(DerivedExt) { d.mutable.set("c") }
        assertEquals("c", n.declaredName)
    }
}
