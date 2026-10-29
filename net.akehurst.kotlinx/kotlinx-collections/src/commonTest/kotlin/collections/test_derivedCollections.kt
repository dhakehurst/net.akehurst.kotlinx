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

class test_derivedCollections {

    private val source = mutableListOf("a", "b", "a")

    @Test
    fun DerivedList_follows_source() {
        val d = DerivedList("X.items", String::class) { source.toList() }
        assertEquals(listOf("a", "b", "a"), d)
        source.add("c")
        assertEquals(4, d.size)
        assertEquals("c", d[3])
    }

    @Test
    fun DerivedSet_follows_source() {
        val d = DerivedSet("X.items", String::class) { source.toSet() }
        assertEquals(setOf("a", "b"), d)
        assertTrue(d.contains("b"))
        source.remove("b")
        assertFalse(d.contains("b"))
    }

    @Test
    fun DerivedOrderedSet_keeps_order() {
        val d = DerivedOrderedSet("X.items", String::class) { source.toOrderedSet() }
        assertEquals(2, d.size)
        assertEquals("a", d[0])
        assertEquals("b", d[1])
        assertEquals(source.toOrderedSet(), d)
    }

    @Test
    fun DerivedCollection_follows_source() {
        val d = DerivedCollection("X.items", String::class) { source.toList() }
        assertEquals(3, d.size)
        source.clear()
        assertTrue(d.isEmpty())
    }

    @Test
    fun derived_collection_rejects_wrong_type() {
        val d = DerivedList<Any>("X.items", String::class) { listOf("a", 1) }
        val e = assertFailsWith<IllegalStateException> { d.size }
        assertEquals("Derived List violation on 'X.items': Expected 'String', but derivation gave 'Int'.", e.message)
    }
}
