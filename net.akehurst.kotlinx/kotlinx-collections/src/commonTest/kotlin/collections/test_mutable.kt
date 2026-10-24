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

class test_mutable {

    @Test
    fun list_mutable_gives_access_to_underlying_mutable_list() = with(ListExt) {
        val list: List<Int> = mutableListOf(1)
        list.mutable.add(2)
        assertEquals(listOf(1, 2), list)
        assertSame<Any>(list, list.mutable)
    }

    @Test
    fun collection_mutable_gives_access_to_underlying_mutable_collection() = with(CollectionExt) {
        val coll: Collection<Int> = mutableListOf(1)
        coll.mutable.add(2)
        assertEquals(listOf(1, 2), coll.toList())
    }

    @Test
    fun collection_toCollection_returns_same_instance() = with(CollectionExt) {
        val coll: Collection<Int> = listOf(1, 2)
        assertSame(coll, coll.toCollection())
    }

    @Test
    fun set_mutable_gives_access_to_underlying_mutable_set() = with(SetExt) {
        val set: Set<Int> = mutableSetOf(1)
        set.mutable.add(2)
        assertEquals(setOf(1, 2), set)
    }

    @Test
    fun orderedSet_mutable_gives_access_to_underlying_mutable_ordered_set() = with(OrderedSetExt) {
        val set: OrderedSet<Int> = mutableOrderedSetOf(1)
        set.mutable.add(2)
        assertEquals(listOf(1, 2), set.toList())
        assertEquals(2, set[1])
    }
}
