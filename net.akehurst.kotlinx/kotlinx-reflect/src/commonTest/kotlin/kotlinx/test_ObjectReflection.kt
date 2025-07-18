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

import kotlin.js.JsExport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

//@JsExport
open class OR_A {
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
            is OR_A ->this.prop1 == other.prop1
            else -> false
        }
    }
}

class OR_B : OR_A() {
    val prop6 = 20
}

class test_ObjectReflection {

    @Test
    fun getProperty() {
        val obj1 = OR_A()
        val actual = obj1.reflect().getProperty("prop1")
        assertEquals(obj1.prop1, actual)
    }

    @Test
    fun isPropertyMutable_false() {
        val obj1 = OR_A()
        val actual = obj1.reflect().isPropertyMutable("prop1")
        assertEquals(false, actual)
    }
    @Test
    fun isPropertyMutable_true() {
        val obj1 = OR_A()
        val actual = obj1.reflect().isPropertyMutable("prop2")
        assertEquals(true, actual)
    }

    @Test
    fun isPropertyMutable_onSupertype_false() {
        val obj1 = OR_B()
        val actual = obj1.reflect().isPropertyMutable("prop1")
        assertEquals(false, actual)
    }
    @Test
    fun isPropertyMutable_onSupertype_true() {
        val obj1 = OR_B()
        val actual = obj1.reflect().isPropertyMutable("prop2")
        assertEquals(true, actual)
    }

    @Test
    fun setProperty() {
        val obj1 = OR_A()
        val actual = obj1.reflect().setProperty("prop2","xxx")
        assertEquals(obj1.prop2, "xxx")
    }

}
