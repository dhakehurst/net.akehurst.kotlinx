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

import net.akehurst.kotlinx.reflect.KotlinxReflect
import net.akehurst.kotlinx.reflect.reflect
import kotlin.test.*

// Overloads with the same number of parameters are told apart by the types of the arguments, the same way on every backend.

open class OV_Base {
    fun pick(s: String) = "base String $s"
}

class OV_Other(val x: Int)

class OV_A(val tag: String) : OV_Base() {
    constructor(n: Int) : this("int:$n")
    constructor(o: OV_Other?) : this("other:${o?.x}")

    fun pick(n: Int) = "Int $n"
    fun pick(items: List<String>) = "List ${items.size}"

    fun any(a: Any) = "Any $a"
    fun any(s: String) = "String $s"
}

class test_Overloads {

    @BeforeTest
    fun before() = registerTestClasses()

    @Test
    fun construct_byArgumentType() {
        assertEquals("x", (OV_A::class.reflect().construct("x") as OV_A).tag)
        assertEquals("int:5", (OV_A::class.reflect().construct(5) as OV_A).tag)
        assertEquals("other:3", (OV_A::class.reflect().construct(OV_Other(3)) as OV_A).tag)
    }

    @Test
    fun construct_null_onlyNullableParameterAccepts() {
        assertEquals("other:null", (OV_A::class.reflect().construct(null) as OV_A).tag)
    }

    @Test
    fun construct_noMatch() {
        val e = assertFailsWith<IllegalStateException> { OV_A::class.reflect().construct(true) }
        assertEquals("No constructor of class 'OV_A' accepts arguments (Boolean)", e.message)
    }

    @Test
    fun call_byArgumentType_includingInherited() {
        assertEquals("base String a", OV_A("x").reflect().call("pick", "a"))
        assertEquals("Int 5", OV_A("x").reflect().call("pick", 5))
        assertEquals("List 2", OV_A("x").reflect().call("pick", listOf("a", "b")))
    }

    @Test
    fun call_noMatch() {
        val e = assertFailsWith<IllegalStateException> { OV_A("x").reflect().call("pick", true) }
        assertEquals("No overload of method 'pick' on class 'OV_A' accepts arguments (Boolean)", e.message)
    }

    @Test
    fun call_ambiguous() {
        val e = assertFailsWith<IllegalStateException> { OV_A("x").reflect().call("any", "s") }
        assertEquals("Several overloads of method 'any' on class 'OV_A' accept arguments (String)", e.message)
        assertEquals("Any 5", OV_A("x").reflect().call("any", 5))
    }

    @Test
    fun registeredParameterTypes() {
        val pick = KotlinxReflect.registeredClass(OV_A::class)!!.functions["pick"]!!
        assertEquals(setOf(Int::class, List::class), pick.map { it.parameterTypes!!.single().kclass }.toSet())
    }
}
