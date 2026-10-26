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
import kotlin.test.assertNotEquals

class test_UniqueIdentityGenerator {

    @Test
    fun generate_numbers_each_prefix_separately() {
        val g = UniqueIdentityGenerator("x")
        assertEquals("xid_0", g.generate())
        assertEquals("xid_1", g.generate())
        assertEquals("xa_0", g.generate("a"))
        assertEquals("xid_2", g.generate())
    }

    @Test
    fun generate_prefix_ending_in_digit_does_not_clash() {
        val g = UniqueIdentityGenerator()
        val fromId1 = g.generate("id1")
        val fromId = (0..10).map { g.generate("id") }.last()
        assertNotEquals(fromId1, fromId)
    }
}
