/*
 * Copyright (C) 2021 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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
 *
 * Based on Original from [http://adtmag.com/articles/2001/07/24/bettermethodfinderjava.aspx]
 * Original license- public domain? code published in article
 * Original Author:  Paul Holser
 * Original Date: 07/24/2001
 *
 */
package net.akehurst.kotlinx.reflect

import net.akehurst.kotlinx.reflect.ClassUtilities.isAssignableFromKClass
import net.akehurst.kotlinx.reflect.ClassUtilities.isAssignableFromKType
import kotlin.reflect.full.starProjectedType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class test_BetterMethodFinder {

    interface XXX
    interface YYY : XXX
    open class AAA
    open class BBB : AAA(), XXX
    class CCC : BBB(), YYY

    class TestClass(val arg:String) {
        constructor(arg2:Int) : this("ByInt")

        fun f(str:String) =str
        fun f(i:Int) =i
    }

    @Test
    fun isAssignableFromKType_self() {
        val types = listOf(String::class.starProjectedType, Int::class.starProjectedType, Boolean::class.starProjectedType)
        assertTrue(types.isAssignableFromKType(types))
    }

    @Test
    fun isAssignableFromKType() {
        val types1 = listOf(AAA::class.starProjectedType, XXX::class.starProjectedType, XXX::class.starProjectedType)
        val types2 = listOf(CCC::class.starProjectedType, BBB::class.starProjectedType, CCC::class.starProjectedType)
        assertTrue(types1.isAssignableFromKType(types2))
    }

    @Test
    fun isAssignableFromKClass_self() {
        val types = listOf(String::class.starProjectedType, Int::class.starProjectedType, Boolean::class.starProjectedType)
        val classes = listOf(String::class, Int::class, Boolean::class)
        assertTrue(types.isAssignableFromKClass(classes))
    }

    @Test
    fun isAssignableFromKClass() {
        val types1 = listOf(AAA::class.starProjectedType, XXX::class.starProjectedType, XXX::class.starProjectedType)
        val classes2 = listOf(CCC::class, BBB::class, CCC::class)
        assertTrue(types1.isAssignableFromKClass(classes2))
    }

    @Test
    fun findConstructorFromParameterTypes_1() {
        val sut = BetterMethodFinder(TestClass::class)

        val c = sut.findConstructorFromParameterTypes<TestClass>(String::class)
        val o = c.call("ByString")
        assertEquals("ByString", o.arg)
    }

    @Test
    fun findConstructorFromParameterTypes_2() {
        val sut = BetterMethodFinder(TestClass::class)

        val c = sut.findConstructorFromParameterTypes<TestClass>(Int::class)
        val o = c.call(1)
        assertEquals("ByInt", o.arg)
    }

    @Test
    fun findConstructorFromArguments() {
        val sut = BetterMethodFinder(TestClass::class)

        val c = sut.findConstructorFromArguments<TestClass>("ByString")
        val o = c.call("ByString")
        assertEquals("ByString", o.arg)
    }

    @Test
    fun findMethodFromArguments_1() {
        val sut = BetterMethodFinder(TestClass::class)

        val m = sut.findMethodFromArguments("f","ByString")
        val r = m.call(TestClass(1),"StringArg")
        assertEquals("StringArg", r)
    }

    @Test
    fun findMethodFromArguments_2() {
        val sut = BetterMethodFinder(TestClass::class)

        val m = sut.findMethodFromArguments("f",2)
        val r = m.call(TestClass(1),2)
        assertEquals(2, r)
    }
}