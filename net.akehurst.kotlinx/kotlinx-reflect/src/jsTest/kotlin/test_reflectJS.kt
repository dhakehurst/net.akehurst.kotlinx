/**
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
 */

package net.akehurst.kotlinx.reflect

import kotlin.reflect.KProperty1
//import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class test_reflectJS {

    @Test
    fun getProperty_KotlinReflection() {
        val obj1 = A()
        fail("Until/If kotlin reflection JS is possible!")
        //val prop:KProperty1<A, String> = obj1::class.memberProperties.find { "prop1"==it.name } as KProperty1<A, String>
        //    ?: fail("Cannot find property")
        //val actual:String = prop.get(obj1)
        //assertEquals(obj1.prop1, actual)
    }

}