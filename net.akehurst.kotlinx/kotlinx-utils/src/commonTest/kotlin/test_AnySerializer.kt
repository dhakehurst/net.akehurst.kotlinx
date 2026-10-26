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

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class test_AnySerializer {

    data class Point(val x: Int, val y: Int)

    private val serializer = AnySerializer(
        customSerialize = { obj ->
            when (obj) {
                is Point -> "Point" to buildJsonObject { put("x", obj.x); put("y", obj.y) }
                else -> error("Can't serialize ${obj::class}")
            }
        },
        customDeserialize = { className, obj ->
            when (className) {
                "Point" -> Point(obj["x"]!!.jsonPrimitive.int, obj["y"]!!.jsonPrimitive.int)
                else -> error("Unknown class $className")
            }
        },
    )
    private val json = Json

    private fun roundTrip(value: Any): Any = json.decodeFromString(serializer, json.encodeToString(serializer, value))

    @Test
    fun plain_values_round_trip() {
        assertEquals("text", roundTrip("text"))
        assertEquals(true, roundTrip(true))
        assertEquals(5_000_000_000L, roundTrip(5_000_000_000L))
        assertEquals(1.5, roundTrip(1.5))
        assertEquals(listOf("a", 1L, null), roundTrip(listOf("a", 1L, null)))
        assertEquals(mapOf("a" to 1L, "b" to listOf(true)), roundTrip(mapOf("a" to 1L, "b" to listOf(true))))
    }

    @Test
    fun custom_objects_round_trip() {
        assertEquals(Point(1, 2), roundTrip(Point(1, 2)))
        assertEquals(mapOf("p" to Point(3, 4)), roundTrip(mapOf("p" to Point(3, 4))))
    }

    @Test
    fun whole_numbers_are_read_as_Long() {
        assertEquals(5L, roundTrip(5))
        assertEquals(-7L, json.decodeFromString(serializer, "-7"))
    }

    @Test
    fun numbers_with_a_point_or_exponent_are_read_as_Double() {
        assertEquals(2.5, json.decodeFromString(serializer, "2.5"))
        assertEquals(1e-7, json.decodeFromString(serializer, "1e-7"))
        assertEquals(1e5, json.decodeFromString(serializer, "1E5"))
        assertEquals(-2.5e3, json.decodeFromString(serializer, "-2.5e+3"))
        assertEquals(listOf(1e21), json.decodeFromString(serializer, "[1e21]"))
    }

    @Test
    fun strings_that_look_like_numbers_stay_strings() {
        assertEquals("1e-7", json.decodeFromString(serializer, "\"1e-7\""))
        assertEquals("42", json.decodeFromString(serializer, "\"42\""))
    }

    @Test
    fun null_read_as_non_null_Any_throws() {
        assertFailsWith<SerializationException> { json.decodeFromString(serializer, "null") }
    }

    @Test
    fun null_read_as_nullable_Any_is_null() {
        assertNull(json.decodeFromString(serializer.nullable, "null"))
    }

    @Test
    fun sets_are_written_as_lists() {
        assertEquals("[1,2,3]", json.encodeToString(serializer, linkedSetOf(1, 2, 3)))
        assertEquals(listOf(1L, 2L), roundTrip(setOf(1, 2)))
    }
}
