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
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertNotNull

class test_HierarchicalReferenceStore {

	@Test
	fun parent_root_identity() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val child = HierarchicalReferenceStoreByHashMap(root, "child")

		assertSame(root, child.parent)
		assertSame(root, root.rootReferenceStore)
		assertSame(root, child.rootReferenceStore)
	}

	@Test
	fun set_get_unqualified() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val child = HierarchicalReferenceStoreByHashMap(root, "child")

		child.set(String::class, "leaf", "L")

		assertEquals("L", child[String::class, "leaf"])
		assertNull(root[String::class, "leaf"]) // unqualified lookup is local only
	}

	@Test
	fun qualified_lookup_from_root() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val child = HierarchicalReferenceStoreByHashMap(root, "child")

		child.set(String::class, "leaf", "L")

		// qualified lookup from root should traverse into registered children
		assertEquals("L", root.getQualifiedReference(String::class, listOf("child","leaf")))
	}

	@Test
	fun resolve_mutable_reference() {
		val root = HierarchicalReferenceStoreByHashMap<Any>(null, "root")
		val child = HierarchicalReferenceStoreByHashMap(root, "child")

		child.set(String::class, "leaf", "L")

		val ref = MutableReferenceDefault<Any, String>(listOf("child","leaf"))
		val resolved = root.resolve(ref)

		assertNotNull(resolved)
		assertEquals("L", resolved)
		assertEquals("L", ref.resolved)
	}
}