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

package net.akehurst.kotlinx.reflect.test

import net.akehurst.kotlinx.reflect.reflect
import kotlin.test.*

abstract class FN_Abstract {
    abstract val name: String
    fun greet(greeting: String) = "$greeting $name"
}

class FN_A(override val name: String) : FN_Abstract() {
    fun add(a: Int, b: Int) = a + b
    fun fail(): Nothing = throw IllegalStateException("failed on purpose")
    suspend fun later(x: Int) = x * 2
}

class test_Functions {

    @BeforeTest
    fun before() = registerTestClasses()

    @Test
    fun isAbstract() {
        assertEquals(true, FN_Abstract::class.reflect().isAbstract)
        assertEquals(false, FN_A::class.reflect().isAbstract)
    }

    @Test
    fun construct_withArgs() {
        val obj = FN_A::class.reflect().construct("bob") as FN_A
        assertEquals("bob", obj.name)
    }

    @Test
    fun construct_wrongNumberOfArgs() {
        val e = assertFailsWith<IllegalStateException> { FN_A::class.reflect().construct() }
        assertEquals("No constructor with 0 parameter(s) found for class 'FN_A'", e.message)
    }

    @Test
    fun call() {
        assertEquals(5, FN_A("x").reflect().call("add", 2, 3))
    }

    @Test
    fun call_inherited() {
        assertEquals("hello bob", FN_A("bob").reflect().call("greet", "hello"))
    }

    @Test
    fun call_notFound() {
        val e = assertFailsWith<IllegalStateException> { FN_A("x").reflect().call("nope") }
        assertEquals("Method 'nope' with 0 parameter(s) not found on class 'FN_A'", e.message)
    }

    @Test
    fun call_throws_originalException() {
        val e = assertFailsWith<IllegalStateException> { FN_A("x").reflect().call("fail") }
        assertEquals("failed on purpose", e.message)
    }

    @Test
    fun call_suspendWithCall_fails() {
        val e = assertFailsWith<IllegalStateException> { FN_A("x").reflect().call("later", 1) }
        assertEquals("Method 'later' on class 'FN_A' is a suspend function, use 'callSuspend' instead of 'call'", e.message)
    }

    @Test
    fun callSuspend() {
        val actual = runSuspend { FN_A("x").reflect().callSuspend("later", 21) }
        assertEquals(42, actual)
    }

    @Test
    fun getProperty_overridden() {
        assertEquals("bob", FN_A("bob").reflect().getProperty("name"))
    }

    @Test
    fun getProperty_notFound() {
        val e = assertFailsWith<IllegalStateException> { FN_A("bob").reflect().getProperty("nope") }
        assertEquals("Property 'nope' not found on class 'FN_A'", e.message)
    }

    @Test
    fun setProperty_notMutable() {
        val e = assertFailsWith<IllegalStateException> { FN_A("bob").reflect().setProperty("name", "x") }
        assertEquals("Property 'name' on class 'FN_A' is not mutable", e.message)
    }

    @Test
    fun isSupertypeOf_false() {
        assertEquals(false, FN_A::class.reflect().isSupertypeOf(FN_Abstract::class))
        assertEquals(false, CR_C::class.reflect().isSupertypeOf(CR_D::class))
    }

    @Test
    fun isSupertypeOf_abstract() {
        assertEquals(true, FN_Abstract::class.reflect().isSupertypeOf(FN_A::class))
    }
}
