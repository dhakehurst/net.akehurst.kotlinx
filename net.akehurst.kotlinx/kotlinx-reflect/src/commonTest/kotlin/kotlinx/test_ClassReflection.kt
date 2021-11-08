/**
 * Copyright (C) 2019 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

package net.akehurst.kotlinx.reflect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail


open class CR_A {
    val prop1 = "hello"
    var prop2 = "world"
    val prop3 = true
    var prop4 = 1
    var prop5 = 3.141

    override fun hashCode(): Int {
        return prop1.hashCode()
    }
    override fun equals(other: Any?): Boolean {
        return when(other) {
            is CR_A ->this.prop1 == other.prop1
            else -> false
        }
    }
}
open class CR_B : CR_A()
open class CR_C : CR_B()
open class CR_D : CR_B()

enum class CR_Colour { RED, GREEN, BLUE }

class test_ClassReflection {

    @Test
    fun A_isSupertypeOf_B() {
        KotlinxReflect.registerClass("net.akehurst.kotlinx.reflect.A",CR_A::class)
        KotlinxReflect.registerClass("net.akehurst.kotlinx.reflect.B",CR_B::class)
        val actual = CR_A::class.reflect().isSupertypeOf(CR_B::class)
        assertEquals(true, actual)
    }

    @Test
    fun A_isSupertypeOf_C() {
        val actual = CR_A::class.reflect().isSupertypeOf(CR_C::class)
        assertEquals(true, actual)
    }

    @Test
    fun allPropertyNames() {
        val actual = CR_A::class.reflect().allPropertyNames
        assertEquals(listOf("prop1","prop2","prop3","prop4","prop5"), actual)
    }

    @Test
    fun construct() {
        KotlinxReflect.registerClass("net.akehurst.kotlinx.reflect.CR_A",CR_A::class)
        val actual = CR_A::class.reflect().construct()
        assertEquals(CR_A::class, actual::class)
    }

    @Test
    fun isPropertyMutable_false() {
        val actual = CR_A::class.reflect().isPropertyMutable("prop1")
        assertEquals(false, actual)
    }
    @Test
    fun isPropertyMutable_true() {
        val actual = CR_A::class.reflect().isPropertyMutable("prop2")
        assertEquals(true, actual)
    }
}
