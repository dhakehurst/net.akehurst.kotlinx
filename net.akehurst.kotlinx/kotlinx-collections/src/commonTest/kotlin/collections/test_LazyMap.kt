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

class test_LazyMap {

    @Test
    fun get_computes_value_from_accessor() {
        val m = lazyMap<String, Int> { it.length }
        assertEquals(3, m["abc"])
        assertEquals(0, m[""])
    }

    @Test
    fun get_caches_value_and_calls_accessor_once_per_key() {
        var calls = 0
        val m = lazyMap<String, Int> { calls++; it.length }
        m["abc"]
        m["abc"]
        m["de"]
        assertEquals(2, calls)
    }

    @Test
    fun null_values_are_cached_too() {
        var calls = 0
        val m = lazyMap<String, String?> { calls++; null }
        assertNull(m["x"])
        assertNull(m["x"])
        assertEquals(1, calls)
    }

    @Test
    fun accessed_keys_are_visible_in_map_views() {
        val m = lazyMap<String, Int> { it.length }
        m["abc"]
        assertTrue(m.containsKey("abc"))
        assertTrue(m.containsValue(3))
        assertTrue(m.keys.contains("abc"))
        assertTrue(m.values.contains(3))
        assertFalse(m.isEmpty())
    }
}

class test_LazyMapNotNull {

    @Test
    fun get_computes_and_caches() {
        var calls = 0
        val m = lazyMapNotNull<String, Int> { calls++; it.length }
        assertEquals(3, m["abc"])
        assertEquals(3, m["abc"])
        assertEquals(1, calls)
        assertTrue(m.containsKey("abc"))
        assertEquals(1, m.size)
    }

    @Test
    fun property_delegation_returns_the_map() {
        val m by lazyMapNotNull<String, Int> { it.length }
        assertEquals(2, m["ab"])
    }
}

class test_LazyMutableMapNotNull {

    @Test
    fun get_computes_and_caches() {
        var calls = 0
        val m = lazyMutableMapNotNull<String, Int> { calls++; it.length }
        assertEquals(3, m["abc"])
        assertEquals(3, m["abc"])
        assertEquals(1, calls)
    }

    @Test
    fun put_overrides_accessor() {
        var calls = 0
        val m = lazyMutableMapNotNull<String, Int> { calls++; it.length }
        m["abc"] = 99
        assertEquals(99, m["abc"])
        assertEquals(0, calls)
    }

    @Test
    fun putAll_overrides_accessor() {
        val m = lazyMutableMapNotNull<String, Int> { it.length }
        m.putAll(mapOf("a" to 10, "b" to 20))
        assertEquals(10, m["a"])
        assertEquals(20, m["b"])
        assertEquals(2, m.size)
    }

    @Test
    fun remove_then_get_recomputes() {
        var calls = 0
        val m = lazyMutableMapNotNull<String, Int> { calls++; it.length }
        m["abc"] = 99
        assertEquals(99, m.remove("abc"))
        assertFalse(m.containsKey("abc"))
        assertEquals(3, m["abc"])
        assertEquals(1, calls)
    }

    @Test
    fun clear_then_get_recomputes() {
        var calls = 0
        val m = lazyMutableMapNotNull<String, Int> { calls++; it.length }
        m["abc"]
        m.clear()
        assertTrue(m.isEmpty())
        m["abc"]
        assertEquals(2, calls)
    }

    @Test
    fun property_delegation_returns_the_map() {
        val m by lazyMutableMapNotNull<String, Int> { it.length }
        m["x"] = 5
        assertEquals(5, m["x"])
    }
}

class test_MutableMapNonNull {

    @Test
    fun get_returns_value_for_present_key() {
        val m = mutableMapNonNullOf<String, Int>()
        m["a"] = 1
        assertEquals(1, m["a"])
        assertEquals(1, m.size)
        assertTrue(m.containsKey("a"))
    }

    @Test
    fun get_for_missing_key_fails() {
        val m = mutableMapNonNullOf<String, Int>()
        assertFails { m["missing"] }
    }

    @Test
    fun remove_and_clear_delegate_to_backing_map() {
        val m = mutableMapNonNullOf<String, Int>()
        m["a"] = 1
        m["b"] = 2
        assertEquals(1, m.remove("a"))
        assertFalse(m.containsKey("a"))
        m.clear()
        assertTrue(m.isEmpty())
    }
}
