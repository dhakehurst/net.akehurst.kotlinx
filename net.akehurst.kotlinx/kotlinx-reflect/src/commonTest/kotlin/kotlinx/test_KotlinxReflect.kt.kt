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


open class KR_A {
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
            is KR_A ->this.prop1 == other.prop1
            else -> false
        }
    }
}

enum class KR_Colour { RED, GREEN, BLUE }
enum class KR_Colour2 { PINK, YELLOW, CYAN }

class test_KotlinxReflect {


    @Test
    fun classForName() {
        KotlinxReflect.registerClass("net.akehurst.kotlinx.reflect.KR_A",KR_A::class)
        val actual = KotlinxReflect.classForName("net.akehurst.kotlinx.reflect.KR_A")
        assertEquals(KR_A::class, actual)
    }

    @Test
    fun isEnum() {
        KotlinxReflect.registerClass("net.akehurst.kotlinx.reflect.KR_A",KR_A::class)
        KotlinxReflect.registerClass("net.akehurst.kotlinx.reflect.KR_Colour",KR_Colour::class)
        assertEquals(false, KR_A::class.reflect().isEnum)
        assertEquals(true, KR_Colour::class.reflect().isEnum)
    }

    @Test
    fun values() {
        KotlinxReflect.registerClass("net.akehurst.kotlinx.reflect.KR_Colour",KR_Colour::class,KR_Colour::values as EnumValuesFunction)
        val actual = KR_Colour::class.reflect().enumValues()
        val expected = KR_Colour.values().asList()
        assertEquals(expected, actual)
    }

    @Test
    fun enumValueOf() {
        KotlinxReflect.registerClass("net.akehurst.kotlinx.reflect.KR_Colour",KR_Colour::class)
        val actual = KR_Colour::class.reflect().enumValueOf("RED")
        assertEquals(KR_Colour.RED, actual)
    }

}
