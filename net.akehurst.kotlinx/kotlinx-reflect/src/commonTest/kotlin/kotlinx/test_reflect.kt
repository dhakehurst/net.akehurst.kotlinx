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


open class A {
    val prop1 = "hello"
    val prop2 = "world"
    val prop3 = true
    val prop4 = 1
    val prop5 = 3.141

    override fun hashCode(): Int {
        return prop1.hashCode()
    }
    override fun equals(other: Any?): Boolean {
        return when(other) {
            is A ->this.prop1 == other.prop1
            else -> false
        }
    }
}
open class B : A()
open class C : B()
open class D : B()

class test_reflect {


    //TODO: not working for js
    //@Test
    fun A_isSupertypeOf_B() {
        val actual = A::class.reflect().isSupertypeOf(B::class)
        assertEquals(true, actual)
    }

    //TODO: not working for js
    //@Test
    fun A_isSupertypeOf_C() {
        val actual = A::class.reflect().isSupertypeOf(C::class)
        assertEquals(true, actual)
    }

    //TODO: not working for js
    //@Test
    fun allPropertyNames() {

        val obj1 = A()
        val actual = obj1::class.reflect().allPropertyNames
        assertEquals(listOf("prop1"), actual)

    }

    @Test
    fun getProperty_KotlinReflection() {
        val obj1 = A()
        fail("Until/If kotlin reflection JS is possible!")
        //val actual = obj1::class.
    }

    @Test
    fun getProperty_myReflection() {
        val obj1 = A()
        val actual = obj1.reflect().getProperty("prop1")
        assertEquals(obj1.prop1, actual)
    }

    @Test
    fun construct() {
        val obj1 = A()
        val actual = obj1::class.reflect().construct()
        assertEquals(obj1, actual)
    }

    //TODO: not currently sure how to test
    //@Test
    fun classForName() {

        val actual = ModuleRegistry.classForName("net.akehurst.kotlinx.reflect.A")
        assertEquals(A::class, actual)

    }
}
