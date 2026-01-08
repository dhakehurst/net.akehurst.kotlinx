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

package net.akehurst.kotlinx.secure.storage

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import net.akehurst.kotlinx.filesystem.api.FileHandle

class SecureStorageDatastore(
    val jsonSerializersModule: SerializersModule,
    val jsonClassDiscriminator: String,
    val secureStorageFile: FileHandle,
    val keyValueSeparator: String,
    val encrypt: Boolean,
    val masterKeyFile: FileHandle?,
    val masterPassword: String?,
) : SecureStorage {

    val serializer = object : NakSerializer<Map<String, String>> {
        override val defaultValue: Map<String, String> get() = emptyMap()

        override fun write(value: Map<String, String>): String {
            val sb = StringBuilder()
            value.forEach { (key, value) -> sb.append("$key$keyValueSeparator$value\n") }
            return sb.toString()
        }

        override fun read(data: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            data.lines().forEach { line ->
                if (line.isNotBlank() && line.contains(keyValueSeparator)) {
                    val split = line.split(keyValueSeparator)
                    val key = split[0]
                    val value = split[1]
                    map[key] = value
                }
            }
            return map
        }
    }
    val encryption = Encryption()

    private val json = Json {
        ignoreUnknownKeys = true
        serializersModule = jsonSerializersModule
        classDiscriminator = jsonClassDiscriminator
    }

    // private val storage = NakFileStorage<Map<String,String>>(secureStorageFile, serializer)
    private val dataStore: MutableStateFlow<Map<String, String>> = MutableStateFlow(mutableMapOf())

    /**
     * Writes a value to secure storage.
     */
    override suspend fun write(key: String, value: Any) {
        val serializedValue = json.encodeToString(value)
        val encrypted = if (encrypt) {
            ensureMasterKey()
            encryption.encryptString(serializedValue)
        } else {
            serializedValue
        }
        dataStore.update { map ->
            map + (key to encrypted)
        }

        coroutineScope {
            launch {
                val content = serializer.write(dataStore.value)
                secureStorageFile.writeContent(content)
            }
        }
    }

    /**
     * Reads a value from secure storage.
     */
    override suspend fun read(key: String): Any? {
        var map = dataStore.asStateFlow().value
        if (map.isEmpty()) {
            val content = secureStorageFile.readContent()
            content?.let {
                map = serializer.read(content)
                dataStore.update { map }
            }
        }
        val encrypted = map[key]
        val decryptedSerialised = encrypted?.let {
            if(encrypt) {
                ensureMasterKey()
                encryption.decryptString(encrypted)
            } else {
                encrypted
            }
        }
        return decryptedSerialised?.let { json.decodeFromString(decryptedSerialised) }
    }

    /**
     * Removes a key from secure storage.
     *
     * @param key The key to remove
     */
    suspend fun delete(key: String) {
        dataStore.update { map ->
            map - key
        }

        coroutineScope {
            launch {
                val content = serializer.write(dataStore.value)
                secureStorageFile.writeContent(content)
            }
        }
    }

    /**
     * Clears all data from secure storage.
     */
    suspend fun clear() {
        dataStore.update { emptyMap() }

        coroutineScope {
            launch {
                val content = serializer.write(dataStore.value)
                secureStorageFile.writeContent(content)
            }
        }
    }

    private suspend fun ensureMasterKey() {
        if (encryption.isLoaded.not()) {
            masterKeyFile ?: error("masterKeyFile must be provided.")
            masterPassword ?: error("masterPassword must be provided.")
            if (masterKeyFile.exists() && masterKeyFile.readContent().isNullOrBlank().not()) {
                encryption.loadKey(masterKeyFile, masterPassword)
            } else {
                encryption.createKey(masterKeyFile, masterPassword)
            }
        }
    }
}

