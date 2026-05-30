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
	fun testNullSetDoesNothing() {
		store[String::class, "r3"] = "keep"
		// setting to null is a no-op in the current implementation
		store[String::class, "r3"] = null
		assertEquals("keep", store[String::class, "r3"])
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

}