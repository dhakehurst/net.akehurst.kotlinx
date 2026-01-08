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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

class AnySerializer(
    val customSerialize: (Any) -> Pair<String, JsonObject> = { _ -> error("customSerialize not initialized") },
    val customDeserialize: (String, JsonObject) -> Any = { _,_ -> error("customDeserialize not initialized") },
) : KSerializer<Any> {

    companion object {
        const val CLASS_DISCRIMINATOR = "#class"
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonEncoder = encoder as JsonEncoder
        val jsonElement = serializeAny(value)
        jsonEncoder.encodeJsonElement(jsonElement)
    }

    private fun serializeAny(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map { serializeAny(it) })
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to serializeAny(v) })
        else -> {
            val (className, jsonObject) = customSerialize.invoke(value)
            JsonObject(
                mapOf(CLASS_DISCRIMINATOR to JsonPrimitive(className)) + jsonObject
            )
        }
    }

    override fun deserialize(decoder: Decoder): Any {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return deserializeJsonElement(element) ?: Unit
    }

    private fun deserializeJsonElement(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> deserializePrimitive(element)
        is JsonArray -> element.map { deserializeJsonElement(it) }
        is JsonObject -> {
            if (element.containsKey(CLASS_DISCRIMINATOR)) {
                val className = element[CLASS_DISCRIMINATOR]!!.jsonPrimitive.content
                val jObj = JsonObject(element.filterKeys { it != CLASS_DISCRIMINATOR })
                customDeserialize.invoke(className, jObj)
            } else {
                element.mapValues { deserializeJsonElement(it.value) }
            }
        }
    }

    private fun deserializePrimitive(primitive: JsonPrimitive): Any? {
        return when {
            primitive.isString -> primitive.content
            primitive.content == "true" || primitive.content == "false" -> primitive.boolean
            primitive.content.contains('.') -> primitive.doubleOrNull ?: primitive.content
            else -> primitive.longOrNull ?: primitive.intOrNull ?: primitive.content
        }
    }
}