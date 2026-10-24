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

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.*

/**
 * Runs a suspend block that never actually suspends, without needing kotlinx-coroutines.
 */
internal fun <T> runSuspend(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
    return (result ?: error("block suspended; runSuspend only supports non-suspending blocks")).getOrThrow()
}

class test_CachedValue {

    @Test
    fun get_computes_and_caches() {
        var calls = 0
        val c = cachedByKey<String, Int> { calls++; it.length }
        assertEquals(3, c["abc"])
        assertEquals(3, c["abc"])
        assertEquals(1, calls)
    }

    @Test
    fun different_keys_are_cached_separately() {
        var calls = 0
        val c = cachedByKey<String, Int> { calls++; it.length }
        assertEquals(1, c["a"])
        assertEquals(2, c["ab"])
        assertEquals(2, calls)
    }

    @Test
    fun null_results_are_cached() {
        var calls = 0
        val c = cachedByKey<String, String?> { calls++; null }
        assertNull(c["x"])
        assertNull(c["x"])
        assertEquals(1, calls)
        assertTrue(c.containsKey("x"))
    }

    @Test
    fun containsKey_and_getCachedValueOrNull_do_not_compute() {
        var calls = 0
        val c = cachedByKey<String, Int> { calls++; it.length }
        assertFalse(c.containsKey("abc"))
        assertNull(c.getCachedValueOrNull("abc"))
        assertEquals(0, calls)
        c["abc"]
        assertTrue(c.containsKey("abc"))
        assertEquals(3, c.getCachedValueOrNull("abc"))
    }

    @Test
    fun set_stores_value_without_computing() {
        var calls = 0
        val c = cachedByKey<String, Int> { calls++; it.length }
        c["abc"] = 42
        assertEquals(42, c["abc"])
        assertEquals(0, calls)
    }

    @Test
    fun reset_forces_recompute_of_one_key() {
        var calls = 0
        val c = cachedByKey<String, Int> { calls++; it.length }
        c["a"]
        c["bb"]
        c.reset("a")
        assertFalse(c.containsKey("a"))
        assertTrue(c.containsKey("bb"))
        c["a"]
        assertEquals(3, calls)
    }

    @Test
    fun resetAll_forces_recompute_of_all_keys() {
        var calls = 0
        val c = cachedByKey<String, Int> { calls++; it.length }
        c["a"]
        c["bb"]
        c.resetAll()
        assertFalse(c.containsKey("a"))
        assertFalse(c.containsKey("bb"))
        c["a"]
        assertEquals(3, calls)
    }

    @Test
    fun replaced_initializer_is_used_after_reset() {
        val c = cachedByKey<String, Int> { 1 }
        assertEquals(1, c["k"])
        c.initializer = { 2 }
        assertEquals(1, c["k"])
        c.reset("k")
        assertEquals(2, c["k"])
    }
}

class test_CachedValueSuspend {

    @Test
    fun get_computes_and_caches() = runSuspend {
        var calls = 0
        val c = cachedByKeySuspend<String, Int> { calls++; it.length }
        assertEquals(3, c["abc"])
        assertEquals(3, c["abc"])
        assertEquals(1, calls)
    }

    @Test
    fun null_results_are_cached() = runSuspend {
        var calls = 0
        val c = cachedByKeySuspend<String, String?> { calls++; null }
        assertNull(c["x"])
        assertNull(c["x"])
        assertEquals(1, calls)
    }

    @Test
    fun containsKey_and_getCachedValueOrNull_do_not_compute() = runSuspend {
        var calls = 0
        val c = cachedByKeySuspend<String, Int> { calls++; it.length }
        assertFalse(c.containsKey("abc"))
        assertNull(c.getCachedValueOrNull("abc"))
        assertEquals(0, calls)
        c["abc"]
        assertTrue(c.containsKey("abc"))
        assertEquals(3, c.getCachedValueOrNull("abc"))
    }

    @Test
    fun set_stores_value_without_computing() = runSuspend {
        var calls = 0
        val c = cachedByKeySuspend<String, Int> { calls++; it.length }
        c["abc"] = 42
        assertEquals(42, c["abc"])
        assertEquals(0, calls)
    }

    @Test
    fun reset_and_resetAll_force_recompute() = runSuspend {
        var calls = 0
        val c = cachedByKeySuspend<String, Int> { calls++; it.length }
        c["a"]
        c["bb"]
        c.reset("a")
        assertFalse(c.containsKey("a"))
        assertTrue(c.containsKey("bb"))
        c.resetAll()
        assertFalse(c.containsKey("bb"))
        c["a"]
        assertEquals(3, calls)
    }
}
