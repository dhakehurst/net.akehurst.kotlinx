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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import net.akehurst.kotlinx.filesystem.api.FileHandle

/**
 * Called the first time a key is needed to encrypt or decrypt.
 * [isNew] is true when there is no key file yet, i.e. a new password is being chosen (so the app can ask for it twice).
 * Return null if the user cancels; the operation then fails with [MasterPasswordCancelledException].
 * If the password is wrong the operation fails with [WrongMasterPasswordException],
 * and the provider is called again by the next operation that needs the key.
 */
typealias MasterPasswordProvider = suspend (isNew: Boolean) -> String?

/**
 * A key/value store kept in [secureStorageFile], one 'key<separator>value' line per entry.
 * When [encrypt] is true each value is encrypted with a key held in [masterKeyFile], which is unlocked
 * with the master password given by [masterPassword] (asked for only when first needed).
 * Keys are stored in plain text; only values are encrypted.
 *
 * All operations are safe to call concurrently. The file is loaded before the first read, write or remove,
 * so existing entries are never lost.
 * On JVM the storage file and key file are made readable and writable by the owner only (POSIX 600 where supported).
 *
 * String values need no [jsonSerializersModule]; other values need a contextual serializer for Any
 * (e.g. AnySerializer from kotlinx-utils).
 */
class SecureStorageDatastore(
    val secureStorageFile: FileHandle,
    val encrypt: Boolean,
    val masterKeyFile: FileHandle? = null,
    val masterPassword: MasterPasswordProvider? = null,
    val jsonSerializersModule: SerializersModule = EmptySerializersModule(),
    val jsonClassDiscriminator: String = "type",
    val keyValueSeparator: String = "=",
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
                    // the value may contain the separator (e.g. base64 padding '='), so split only once
                    val key = line.substringBefore(keyValueSeparator)
                    val value = line.substringAfter(keyValueSeparator)
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

    private val mutex = Mutex()

    // guarded by mutex; null until the file has been loaded
    private var entries: Map<String, String>? = null

    /** true once the master key is loaded, i.e. the password has been given (always false when [encrypt] is false) */
    val isUnlocked: Boolean get() = encryption.isLoaded

    /**
     * Writes a value. A String is stored as is; other values need a contextual serializer for Any in [jsonSerializersModule].
     */
    override suspend fun write(key: String, value: Any) {
        val serializedValue = when (value) {
            is String -> json.encodeToString(String.serializer(), value)
            else -> json.encodeToString(value)
        }
        mutex.withLock {
            val stored = if (encrypt) {
                ensureMasterKey()
                encryption.encryptString(serializedValue)
            } else {
                serializedValue
            }
            save(loaded() + (key to stored))
        }
    }

    /** Writes a String value (same as [write]) */
    suspend fun writeString(key: String, value: String) = write(key, value)

    /**
     * Reads a value, or null if there is no entry for the key.
     * A stored String is returned as a String without needing [jsonSerializersModule].
     */
    override suspend fun read(key: String): Any? {
        val serialized = readSerialized(key) ?: return null
        val element = json.parseToJsonElement(serialized)
        return when {
            element is JsonPrimitive && element.isString -> element.content
            else -> json.decodeFromString<Any>(serialized)
        }
    }

    /** Reads a String value; null if there is no entry for the key; a value that is not a String is given by its toString() */
    suspend fun readString(key: String): String? = read(key)?.let { it as? String ?: it.toString() }

    /** the keys of all entries (no password is needed, keys are not encrypted) */
    suspend fun keys(): Set<String> = mutex.withLock { loaded().keys }

    /**
     * Removes the entry for the key, if there is one.
     */
    suspend fun remove(key: String) {
        mutex.withLock {
            val map = loaded()
            if (map.containsKey(key)) save(map - key)
        }
    }

    /** same as [remove] */
    suspend fun delete(key: String) = remove(key)

    /**
     * Removes all entries. The key file and master password are kept.
     */
    suspend fun clear() {
        mutex.withLock { save(emptyMap()) }
    }

    /**
     * For a forgotten master password: deletes the key file and the storage file (where the platform cannot delete,
     * they are made empty). All stored values are lost. The next operation that needs a key creates a new one,
     * calling the password provider with isNew = true.
     */
    suspend fun reset() {
        mutex.withLock {
            encryption.unload()
            masterKeyFile?.let { deleteOrEmpty(it) }
            deleteOrEmpty(secureStorageFile)
            entries = emptyMap()
        }
    }

    private suspend fun readSerialized(key: String): String? = mutex.withLock {
        val stored = loaded()[key] ?: return@withLock null
        if (encrypt) {
            ensureMasterKey()
            encryption.decryptString(stored)
        } else {
            stored
        }
    }

    // call only while holding mutex
    private suspend fun loaded(): Map<String, String> = entries ?: run {
        val content = secureStorageFile.readContent()
        val map = if (content.isNullOrBlank()) emptyMap() else serializer.read(content)
        entries = map
        map
    }

    // call only while holding mutex
    private suspend fun save(map: Map<String, String>) {
        writeOwnerOnly(secureStorageFile, serializer.write(map))
        entries = map
    }

    // call only while holding mutex
    private suspend fun ensureMasterKey() {
        if (encryption.isLoaded) return
        val keyFile = masterKeyFile ?: error("masterKeyFile must be provided when encrypt is true.")
        val provider = masterPassword ?: error("masterPassword must be provided when encrypt is true.")
        val isNew = keyFile.exists().not() || keyFile.readContent().isNullOrBlank()
        val password = provider.invoke(isNew) ?: throw MasterPasswordCancelledException()
        if (isNew) {
            encryption.createKey(keyFile, password)
        } else {
            encryption.loadKey(keyFile, password)
        }
    }
}
