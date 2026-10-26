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

import net.akehurst.kotlinx.utils.ReferenceStoreExt.resolve
import kotlin.test.*

class test_ReferenceStoreByHashMap {

	private var store = ReferenceStoreByHashMap<String>()


	@Test
	fun testStoreAndRetrieveSingle() {
		store[String::class, "r1"] = "hello"
		assertEquals("hello", store[String::class, "r1"])
		// wrong class should return null
		assertNull(store[Int::class, "r1"])
	}

	@Test
	fun testOverwriteValue() {
		store[String::class, "r2"] = "first"
		store[String::class, "r2"] = "second"
		assertEquals("second", store[String::class, "r2"])
	}

	@Test
	fun testNullSetRemoves() {
		store[String::class, "r3"] = "gone"
		store[String::class, "r3"] = null
		assertNull(store[String::class, "r3"])
	}

	@Test
	fun testRemove() {
		store[String::class, "r4"] = "gone"
		store[String::class, "r5"] = "kept"
		store.remove(String::class, "r4")
		assertNull(store[String::class, "r4"])
		assertEquals("kept", store[String::class, "r5"])
	}

	@Test
	fun testRemoveOnlyAffectsThatType() {
		store[String::class, "shared"] = "s"
		store[Int::class, "shared"] = 42
		store.remove(String::class, "shared")
		assertNull(store[String::class, "shared"])
		assertEquals(42, store[Int::class, "shared"])
	}

	@Test
	fun testRemoveMissingDoesNothing() {
		store.remove(String::class, "never-set")
		store[String::class, "r6"] = "x"
		store.remove(String::class, "never-set")
		assertEquals("x", store[String::class, "r6"])
	}

	@Test
	fun testResolveAfterRemoveIsNull() {
		store[String::class, "r7"] = "v"
		store.remove(String::class, "r7")
		assertNull(store.resolve(MutableReferenceDefault<String, String>("r7")))
	}

	@Test
	fun testMultipleTypes() {
		// same reference key but different stored types
		store[String::class, "shared"] = "s"
		store[Int::class, "shared"] = 42

		assertEquals("s", store[String::class, "shared"])
		assertEquals(42, store[Int::class, "shared"])
		// retrieving with wrong class returns null
		assertNull(store[Double::class, "shared"])
	}

	@Test
	fun testResolveSetsMutableReference() {
		// prepare store and a mutable reference
		store[String::class, "ref-x"] = "resolved-value"
		val ref = MutableReferenceDefault<String, String>("ref-x")

		val resolved = store.resolve(ref)

		assertEquals("resolved-value", resolved)
		// when resolved, the mutable reference should have its resolved field set
		assertEquals("resolved-value", ref.resolved)
	}

	@Test
	fun testResolveSetsResolvableReferences() {
		store[String::class, "k"] = "v"
		val plain = ResolvableReferenceDefault<String, String>("k")
		val managed = ManagedResolvableReference<String, String>("k", "m", String::class)
		assertEquals("v", store.resolve(plain))
		assertEquals("v", store.resolve(managed))
		assertEquals("v", plain.resolved)
		assertEquals("v", managed.resolved)
	}

	@Test
	fun testResolveNotFoundKeepsResolvedByDefault() {
		val ref = MutableReferenceDefault<String, String>("k")
		ref.resolved = "old"
		assertNull(store.resolve(ref))
		assertEquals("old", ref.resolved)
	}

	@Test
	fun testResolveNotFoundClearsWhenAsked() {
		store[String::class, "k"] = "v"
		val ref = ResolvableReferenceDefault<String, String>("k")
		store.resolve(ref)
		store.remove(String::class, "k")

		assertNull(store.resolve(ref, clearIfNotFound = true))
		assertNull(ref.resolved)
	}

	@Test
	fun testResolveFoundWithClearIfNotFoundStillSets() {
		store[String::class, "k"] = "v"
		val ref = ResolvableReferenceDefault<String, String>("k")
		assertEquals("v", store.resolve(ref, clearIfNotFound = true))
		assertEquals("v", ref.resolved)
	}

	@Test
	fun testResolveNoKeyNeverClears() {
		val ref = MutableReferenceDefault<String, String>(null)
		ref.resolved = "by value"
		assertNull(store.resolve(ref, clearIfNotFound = true))
		assertEquals("by value", ref.resolved)
	}

}
