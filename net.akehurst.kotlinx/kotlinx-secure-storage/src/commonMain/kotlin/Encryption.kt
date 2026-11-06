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

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import net.akehurst.kotlinx.filesystem.api.FileHandle
import kotlin.io.encoding.Base64

/**
 * Holds the AES-GCM key used to encrypt values.
 *
 * The key is random. It is stored in a key file, encrypted ("wrapped") with a key derived from a master password
 * using PBKDF2-SHA256 and a random salt. The key file is JSON:
 * ```
 * {"version":1,"kdf":"PBKDF2-SHA256","iterations":600000,"salt":"<base64>","key":"<base64 wrapped key>"}
 * ```
 * Key files written by older versions (just the base64 wrapped key, derived with an all-zero salt) can still be loaded;
 * [loadKey] rewrites them in the current format with a new random salt.
 */
class Encryption(
    private val provider: CryptographyProvider = CryptographyProvider.Default,
    /** PBKDF2 iterations used when a key file is written; the value is stored in the key file, so it can change later */
    val pbkdf2Iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
) {

    companion object {
        const val KEY_FILE_VERSION = 1
        const val KDF_PBKDF2_SHA256 = "PBKDF2-SHA256"
        const val SALT_SIZE_BYTES = 16
        const val DEFAULT_PBKDF2_ITERATIONS = 600_000 // OWASP recommendation for PBKDF2-HMAC-SHA256
        internal const val LEGACY_PBKDF2_ITERATIONS = 100_000
    }

    private class KeyFileContent(val iterations: Int, val salt: ByteArray, val wrappedKey: ByteArray, val isLegacy: Boolean)

    var encryptionKey: AES.GCM.Key? = null
        private set

    val isLoaded: Boolean get() = null != encryptionKey

    /** forget the loaded key (the key file is not changed) */
    fun unload() {
        encryptionKey = null
    }

    /**
     * Creates a new random encryption key, stores it in the key file wrapped with the master password, and loads it.
     * Any existing key file is replaced.
     */
    suspend fun createKey(keyFile: FileHandle, masterPassword: String) {
        val aes = provider.get(AES.GCM)
        val key = aes.keyGenerator().generateKey()
        writeKeyFile(keyFile, key, masterPassword)
        this.encryptionKey = key
    }

    /**
     * Loads the encryption key from the key file and unwraps it with the master password.
     * @throws WrongMasterPasswordException if the password does not unlock the key
     * @throws InvalidKeyFileException if the key file is empty or not in a known format
     */
    suspend fun loadKey(keyFile: FileHandle, masterPassword: String) {
        val text = keyFile.readContent()
        if (text.isNullOrBlank()) throw InvalidKeyFileException("Key file '${keyFile.name}' is empty.")
        val content = parseKeyFile(text)

        val masterKey = deriveKeyFromPassword(masterPassword, content.salt, content.iterations)
        val keyBytes = try {
            masterKey.cipher().decrypt(content.wrappedKey)
        } catch (e: Exception) {
            // AES-GCM authentication fails when the password (hence the derived key) is wrong
            throw WrongMasterPasswordException(cause = e)
        }
        val key = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
        this.encryptionKey = key

        if (content.isLegacy) {
            // re-wrap the same key with a random salt; the stored values stay readable
            writeKeyFile(keyFile, key, masterPassword)
        }
    }

    /**
     * Encrypts a string with the loaded key
     */
    suspend fun encryptString(plaintext: String): String {
        val cipher = encryptionKey?.cipher() ?: error("Master encryption key is not loaded.")
        val ciphertext = cipher.encrypt(plaintext.encodeToByteArray())
        return Base64.encode(ciphertext)
    }

    /**
     * Decrypts a string with the loaded key
     */
    suspend fun decryptString(ciphertext: String): String {
        val encryptedData = Base64.decode(ciphertext)
        val cipher = encryptionKey?.cipher() ?: error("Master encryption key is not loaded.")
        val decryptedBytes = cipher.decrypt(encryptedData)
        return decryptedBytes.decodeToString()
    }

    private suspend fun writeKeyFile(keyFile: FileHandle, key: AES.GCM.Key, masterPassword: String) {
        val salt = CryptographyRandom.nextBytes(SALT_SIZE_BYTES)
        val masterKey = deriveKeyFromPassword(masterPassword, salt, pbkdf2Iterations)
        val wrappedKey = masterKey.cipher().encrypt(key.encodeToByteArray(AES.Key.Format.RAW))
        val json = buildJsonObject {
            put("version", KEY_FILE_VERSION)
            put("kdf", KDF_PBKDF2_SHA256)
            put("iterations", pbkdf2Iterations)
            put("salt", Base64.encode(salt))
            put("key", Base64.encode(wrappedKey))
        }
        writeOwnerOnly(keyFile, json.toString())
    }

    private fun parseKeyFile(text: String): KeyFileContent {
        val trimmed = text.trim()
        return if (trimmed.startsWith("{")) {
            val obj: JsonObject = try {
                Json.parseToJsonElement(trimmed).jsonObject
            } catch (e: Exception) {
                throw InvalidKeyFileException("Key file is not valid JSON.", e)
            }
            val version = (obj["version"] as? JsonPrimitive)?.intOrNull
            if (version != KEY_FILE_VERSION) throw InvalidKeyFileException("Key file version '$version' is not supported.")
            val kdf = (obj["kdf"] as? JsonPrimitive)?.contentOrNull
            if (kdf != KDF_PBKDF2_SHA256) throw InvalidKeyFileException("Key file kdf '$kdf' is not supported.")
            val iterations = (obj["iterations"] as? JsonPrimitive)?.intOrNull
                ?: throw InvalidKeyFileException("Key file has no 'iterations'.")
            val salt = obj.base64("salt")
            val key = obj.base64("key")
            KeyFileContent(iterations, salt, key, isLegacy = false)
        } else {
            // format before version 1: base64 wrapped key, all-zero salt
            val key = try {
                Base64.decode(trimmed)
            } catch (e: Exception) {
                throw InvalidKeyFileException("Key file is not in a known format.", e)
            }
            KeyFileContent(LEGACY_PBKDF2_ITERATIONS, ByteArray(SALT_SIZE_BYTES), key, isLegacy = true)
        }
    }

    private fun JsonObject.base64(name: String): ByteArray {
        val s = (this[name] as? JsonPrimitive)?.contentOrNull ?: throw InvalidKeyFileException("Key file has no '$name'.")
        return try {
            Base64.decode(s)
        } catch (e: Exception) {
            throw InvalidKeyFileException("Key file '$name' is not valid base64.", e)
        }
    }

    /**
     * Derives an AES-GCM key from a master password using PBKDF2-SHA256
     * (used for wrapping the stored encryption key)
     */
    private suspend fun deriveKeyFromPassword(password: String, salt: ByteArray, iterations: Int): AES.GCM.Key {
        val secretDerivation = provider.get(PBKDF2).secretDerivation(
            digest = SHA256,
            iterations = iterations,
            outputSize = 32.bytes, // AES-256
            salt = salt
        )
        val derivedKeyBytes = secretDerivation.deriveSecret(password.encodeToByteArray())
        return provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, derivedKeyBytes.toByteArray())
    }
}
