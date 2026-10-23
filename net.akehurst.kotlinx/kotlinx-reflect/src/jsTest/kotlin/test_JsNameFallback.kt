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

package net.akehurst.kotlinx.reflect.jstest

import net.akehurst.kotlinx.reflect.KotlinxReflect
import net.akehurst.kotlinx.reflect.reflect
import kotlin.test.*

// Not registered with KotlinxReflect: found by their JavaScript names, which @JsExport keeps unmangled
@JsExport
open class JS_A {
    val prop1 = "hello"
    var prop2 = "world"
    fun twice(x: Int) = x * 2
}

@JsExport
class JS_B : JS_A() {
    val prop3 = 3
}

// Registered, but its properties are not
@JsExport
class JS_C {
    var name = "bob"
}

@JsExport
enum class JS_Colour { RED, GREEN }

// Not registered: an object is recognised, but its instance is not available
@JsExport
object JS_Object {
    val x = 1
}

// Registered with only some of its properties: only those are listed
@JsExport
class JS_D {
    val listed = 1
    internal val notListed = 2
}

class test_JsNameFallback {

    @BeforeTest
    fun before() {
        KotlinxReflect.forceRegistry = false
        KotlinxReflect.registerClass("net.akehurst.kotlinx.reflect.jstest.JS_C", JS_C::class)
        KotlinxReflect.registerClass("net.akehurst.kotlinx.reflect.jstest.JS_D", JS_D::class)
        KotlinxReflect.registerProperty(JS_D::class, JS_D::listed)
    }

    @Test
    fun object_unregistered_isRecognised_butNotConstructed() {
        assertEquals(true, JS_Object::class.reflect().isObject)
        val e = assertFailsWith<IllegalStateException> { JS_Object::class.reflect().construct() }
        assertEquals("Object 'JS_Object' is not registered with KotlinxReflect, so its instance is not available", e.message)
    }

    @Test
    fun enum_unregistered_doesNotListStdlibProperties() {
        assertEquals(emptyList(), JS_Colour::class.reflect().allPropertyNames)
    }

    @Test
    fun registeredClass_listsOnlyRegisteredProperties() {
        assertEquals(listOf("listed"), JS_D().reflect().allPropertyNames)
    }

    @AfterTest
    fun after() {
        KotlinxReflect.forceRegistry = false
    }

    @Test
    fun allPropertyNames_unregistered() {
        assertEquals(listOf("prop1", "prop2", "prop3"), JS_B().reflect().allPropertyNames)
    }

    @Test
    fun getProperty_unregistered() {
        assertEquals("hello", JS_B().reflect().getProperty("prop1"))
        assertEquals(3, JS_B().reflect().getProperty("prop3"))
    }

    @Test
    fun getProperty_unregistered_notFound() {
        val e = assertFailsWith<IllegalStateException> { JS_A().reflect().getProperty("nope") }
        assertEquals("Property 'nope' not found on class 'JS_A'", e.message)
    }

    @Test
    fun setProperty_unregistered() {
        val obj = JS_A()
        obj.reflect().setProperty("prop2", "xxx")
        assertEquals("xxx", obj.prop2)
    }

    @Test
    fun setProperty_unregistered_notMutable() {
        val e = assertFailsWith<IllegalStateException> { JS_A().reflect().setProperty("prop1", "xxx") }
        assertEquals("Property 'prop1' on class 'JS_A' is not mutable", e.message)
    }

    @Test
    fun isPropertyMutable_unregistered() {
        assertEquals(false, JS_B().reflect().isPropertyMutable("prop1"))
        assertEquals(true, JS_B().reflect().isPropertyMutable("prop2"))
        assertEquals(false, JS_B().reflect().isPropertyMutable("nope"))
    }

    @Test
    fun call_unregistered() {
        assertEquals(42, JS_B().reflect().call("twice", 21))
    }

    @Test
    fun construct_unregistered() {
        val obj = JS_B::class.reflect().construct()
        assertTrue(obj is JS_B)
    }

    @Test
    fun isSupertypeOf_unregistered() {
        assertEquals(true, JS_A::class.reflect().isSupertypeOf(JS_B::class))
        assertEquals(false, JS_B::class.reflect().isSupertypeOf(JS_A::class))
    }

    @Test
    fun enum_unregistered() {
        assertEquals(true, JS_Colour::class.reflect().isEnum)
        assertEquals(JS_Colour.GREEN, JS_Colour::class.reflect().enumValueOf<JS_Colour>("GREEN"))
    }

    @Test
    fun registeredClass_unregisteredProperty() {
        val obj = JS_C()
        assertEquals("bob", obj.reflect().getProperty("name"))
        obj.reflect().setProperty("name", "alice")
        assertEquals("alice", obj.name)
    }

    @Test
    fun forceRegistry_disablesFallback() {
        KotlinxReflect.forceRegistry = true
        assertFailsWith<IllegalStateException> { JS_A().reflect().getProperty("prop1") }
    }
}
