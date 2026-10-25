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

import net.akehurst.kotlinx.utils.HierarchicalReferenceStoreExt.resolve
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class test_HierarchicalReferenceStore {

	interface Shape
	data class Circle(val name: String) : Shape
	data class Square(val name: String) : Shape

	@Test
	fun parent_root_identity() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val child = HierarchicalReferenceStoreByHashMap(root, "child")

		assertSame(root, child.parentReferenceStore)
		assertSame(root, root.rootReferenceStore)
		assertSame(root, child.rootReferenceStore)
	}

	@Test
	fun set_get_unqualified() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val child = HierarchicalReferenceStoreByHashMap(root, "child")

		child.set(String::class, "leaf", "L")

		assertEquals("L", child[String::class, "leaf"])
		assertNull(root[String::class, "leaf"]) // get only looks in the store itself
	}

	@Test
	fun resolve_from_root_finds_object_in_child() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val child = HierarchicalReferenceStoreByHashMap(root, "child")

		child.set(String::class, "leaf", "L")

		assertEquals("L", root.resolve(MutableReferenceDefault<String, String>("leaf")))
	}

	@Test
	fun resolve_mutable_reference() {
		val root = HierarchicalReferenceStoreByHashMap<Any>(null, "root")
		val child = HierarchicalReferenceStoreByHashMap(root, "child")

		child.set(String::class, "leaf", "L")

		val ref = MutableReferenceDefault<Any, String>("leaf")
		val resolved = root.resolve(ref)

		assertEquals("L", resolved)
		assertEquals("L", ref.resolved)
	}

	@Test
	fun resolve_from_child_finds_object_in_sibling() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val childA = HierarchicalReferenceStoreByHashMap(root, "a")
		val childB = HierarchicalReferenceStoreByHashMap(root, "b")

		childB.set(String::class, "leaf", "L")

		assertEquals("L", childA.resolve(MutableReferenceDefault<String, String>("leaf")))
	}

	@Test
	fun resolve_unknown_key_returns_null() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val child = HierarchicalReferenceStoreByHashMap(root, "child")
		child.set(String::class, "leaf", "L")

		val ref = MutableReferenceDefault<String, String>("other")
		assertNull(root.resolve(ref))
		assertNull(ref.resolved)
	}

	@Test
	fun same_store_can_set_a_type_many_times() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val child = HierarchicalReferenceStoreByHashMap(root, "child")

		child.set(String::class, "a", "A")
		child.set(String::class, "b", "B")

		assertEquals("A", root.resolve(MutableReferenceDefault<String, String>("a")))
		assertEquals("B", root.resolve(MutableReferenceDefault<String, String>("b")))
	}

	@Test
	fun two_stores_setting_the_same_type_throws() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val childA = HierarchicalReferenceStoreByHashMap(root, "a")
		val childB = HierarchicalReferenceStoreByHashMap(root, "b")

		childA.set(String::class, "x", "X")

		assertFailsWith<IllegalStateException> { childB.set(String::class, "y", "Y") }
		assertNull(childB[String::class, "y"]) // nothing stored when it fails
	}

	@Test
	fun resolve_supertype_reference_finds_subtype_object() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val child = HierarchicalReferenceStoreByHashMap(root, "child")
		val c1 = Circle("c1")
		child.set(Circle::class, "c1", c1)

		val ref = MutableReferenceDefault<String, Shape>("c1")

		assertSame(c1, root.resolve(ref))
		assertSame(c1, ref.resolved)
	}

	@Test
	fun resolve_supertype_reference_finds_subtype_in_any_store() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val circles = HierarchicalReferenceStoreByHashMap(root, "circles")
		val squares = HierarchicalReferenceStoreByHashMap(root, "squares")
		val c1 = Circle("c1")
		val s1 = Square("s1")
		circles.set(Circle::class, "c1", c1)
		squares.set(Square::class, "s1", s1)

		assertSame(c1, root.resolve(MutableReferenceDefault<String, Shape>("c1")))
		assertSame(s1, root.resolve(MutableReferenceDefault<String, Shape>("s1")))
	}

	@Test
	fun resolve_ignores_same_key_with_unrelated_type() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val child = HierarchicalReferenceStoreByHashMap(root, "child")
		child.set(String::class, "x", "not a shape")

		assertNull(root.resolve(MutableReferenceDefault<String, Shape>("x")))
	}

	@Test
	fun resolve_prefers_exact_type_over_subtype() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val shapes = HierarchicalReferenceStoreByHashMap(root, "shapes")
		val circles = HierarchicalReferenceStoreByHashMap(root, "circles")
		val asShape = Circle("as shape")
		val asCircle = Circle("as circle")
		shapes.set(Shape::class, "x", asShape)
		circles.set(Circle::class, "x", asCircle)

		assertSame(asShape, root.resolve(MutableReferenceDefault<String, Shape>("x")))
		assertSame(asCircle, root.resolve(MutableReferenceDefault<String, Circle>("x")))
	}

	@Test
	fun resolve_supertype_reference_matching_several_objects_throws() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val circles = HierarchicalReferenceStoreByHashMap(root, "circles")
		val squares = HierarchicalReferenceStoreByHashMap(root, "squares")
		circles.set(Circle::class, "x", Circle("x"))
		squares.set(Square::class, "x", Square("x"))

		assertFailsWith<IllegalStateException> { root.resolve(MutableReferenceDefault<String, Shape>("x")) }
	}

	// --- removing ---

	@Test
	fun remove_then_get_and_resolve_give_null() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val child = HierarchicalReferenceStoreByHashMap(root, "child")
		child.set(String::class, "leaf", "L")

		child.remove(String::class, "leaf")

		assertNull(child[String::class, "leaf"])
		assertNull(root.resolve(MutableReferenceDefault<String, String>("leaf")))
	}

	@Test
	fun set_null_removes() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val child = HierarchicalReferenceStoreByHashMap(root, "child")
		child.set(String::class, "leaf", "L")

		child.set(String::class, "leaf", null)

		assertNull(child[String::class, "leaf"])
	}

	@Test
	fun ownership_is_kept_after_last_object_removed() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val childA = HierarchicalReferenceStoreByHashMap(root, "a")
		val childB = HierarchicalReferenceStoreByHashMap(root, "b")
		childA.set(String::class, "x", "X")

		childA.remove(String::class, "x")

		assertSame(childA, root.getOwnerForReferenceType(String::class))
		assertFailsWith<IllegalStateException> { childB.set(String::class, "y", "Y") }
		childA.set(String::class, "z", "Z") // the owner can still add more
		assertEquals("Z", root.resolve(MutableReferenceDefault<String, String>("z")))
	}

	@Test
	fun removing_a_type_never_held_does_not_take_ownership() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val childA = HierarchicalReferenceStoreByHashMap(root, "a")
		val childB = HierarchicalReferenceStoreByHashMap(root, "b")

		childA.remove(String::class, "x")
		childA.set(String::class, "y", null)

		assertNull(root.getOwnerForReferenceType(String::class))
		childB.set(String::class, "x", "X") // b can still become the owner
		assertSame(childB, root.getOwnerForReferenceType(String::class))
	}

	@Test
	fun remove_from_another_store_does_nothing() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val childA = HierarchicalReferenceStoreByHashMap(root, "a")
		val childB = HierarchicalReferenceStoreByHashMap(root, "b")
		childA.set(String::class, "x", "X")

		childB.remove(String::class, "x")

		assertEquals("X", childA[String::class, "x"])
		assertSame(childA, root.getOwnerForReferenceType(String::class))
	}

	@Test
	fun resolved_reference_keeps_removed_object_until_resolved_again() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val child = HierarchicalReferenceStoreByHashMap(root, "child")
		child.set(String::class, "leaf", "L")
		val ref = MutableReferenceDefault<String, String>("leaf")
		root.resolve(ref)

		child.remove(String::class, "leaf")

		assertEquals("L", ref.resolved) // stores don't track references
		assertNull(root.resolve(ref))
	}

	@Test
	fun resolve_sets_resolvable_references() {
		val root = HierarchicalReferenceStoreByHashMap<String>(null, "root")
		val child = HierarchicalReferenceStoreByHashMap(root, "child")
		val c1 = Circle("c1")
		child.set(Circle::class, "c1", c1)
		val plain = ResolvableReferenceDefault<String, Shape>("c1")
		val managed = ManagedResolvableReference<String, Shape>("c1", "m", Shape::class)

		assertSame(c1, root.resolve(plain))
		assertSame(c1, root.resolve(managed))
		assertSame(c1, plain.resolved)
		assertSame(c1, managed.resolved)
	}
}
