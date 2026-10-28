/**
 * Copyright (C) 2026 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.akehurst.kotlinx.secure.storage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class test_SecretSource {

    @Test
    fun in_memory_source() = runTest {
        val sut = SecretSourceInMemory("mem", mapOf("a" to "1"))
        assertEquals("1", sut.read("a"))
        assertNull(sut.read("b"))
        sut.set("b", "2")
        assertEquals("2", sut.read("b"))
        sut.remove("a")
        assertNull(sut.read("a"))
    }

    @Test
    fun registry() {
        val reg = SecretSourceRegistryDefault()
        val src = SecretSourceInMemory("mem")
        reg.register(src)
        assertSame(src, reg.getOrNull("mem"))
        assertNull(reg.getOrNull("other"))
    }
}

class test_SecretSourceEnvironment {

    @Test
    fun unset_variable_gives_null() = runTest {
        val sut = SecretSourceEnvironment()
        assertNull(sut.read("NAK_SECRET_SOURCE_TEST_NOT_SET_7f3a"))
    }
}
