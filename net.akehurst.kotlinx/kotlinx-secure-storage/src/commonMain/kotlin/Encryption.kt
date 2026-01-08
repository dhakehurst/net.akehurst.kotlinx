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

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import net.akehurst.kotlinx.filesystem.api.FileHandle

@OptIn(ExperimentalEncodingApi::class)
class Encryption(
    private val provider: CryptographyProvider = CryptographyProvider.Default
) {

    var encryptionKey: AES.GCM.Key? = null

    val isLoaded: Boolean get() = null != encryptionKey

    /**
     * Creates a new encryption key from a master password and stores it encrypted in the key file
     */
    suspend fun createKey(keyFile: FileHandle, masterPassword: String) {
        // Generate a random AES encryption key
        val aes = provider.get(AES.GCM)
        val encryptionKey = aes.keyGenerator().generateKey()
        val encryptionKeyBytes = encryptionKey.encodeToByteArray(AES.Key.Format.RAW)

        // Derive a master key from the master password to encrypt the encryption key
        val masterKey = deriveKeyFromPassword(masterPassword)
        val masterCipher = masterKey.cipher()
        val encryptedKeyBytes = masterCipher.encrypt(encryptionKeyBytes)

        // Store the encrypted key in the file
        val encodedKey = Base64.encode(encryptedKeyBytes)
        keyFile.writeContent(encodedKey)

        loadKey(keyFile, masterPassword)
    }

    /**
     * Loads the encryption key from file and decrypts it with the master password
     */
    suspend fun loadKey(keyFile: FileHandle, masterPassword: String) {
        // Read the encrypted key from file
        val encodedKey = keyFile.readContent() ?: throw IllegalStateException("Key file is empty")
        val encryptedKeyBytes = Base64.decode(encodedKey)

        // Derive the master key from password
        val masterKey = deriveKeyFromPassword(masterPassword)
        val masterCipher = masterKey.cipher()
        val decryptedKeyBytes = masterCipher.decrypt(encryptedKeyBytes)

        // Reconstruct the AES encryption key from the decrypted bytes
        val aes = provider.get(AES.GCM)
        val keyDecoder = aes.keyDecoder()
        val key = keyDecoder.decodeFromByteArray(AES.Key.Format.RAW, decryptedKeyBytes)
        this.encryptionKey = key
    }

    /**
     * Encrypts a string with an AES.GCM key
     */
    suspend fun encryptString(plaintext: String): String {
        val cipher = encryptionKey?.cipher() ?: error("Master encryption key is not loaded.")
        val ciphertext = cipher.encrypt(plaintext.encodeToByteArray())
        return Base64.encode(ciphertext)
    }

    /**
     * Decrypts a string with an AES.GCM key
     */
    suspend fun decryptString(ciphertext: String): String {
        val encryptedData = Base64.decode(ciphertext)
        val cipher = encryptionKey?.cipher() ?: error("Master encryption key is not loaded.")
        val decryptedBytes = cipher.decrypt(encryptedData)
        return decryptedBytes.decodeToString()
    }

    /**
     * Derives an AES.GCM key from a master password using PBKDF2
     * (used for encrypting the stored encryption key)
     */
    private suspend fun deriveKeyFromPassword(password: String): AES.GCM.Key {
        val pbkdf2 = provider.get(PBKDF2)
        val salt = ByteArray(16) { 0 } // Use zero-filled salt for deterministic key derivation

        // Use PBKDF2 secretDerivation API
        val secretDerivation = pbkdf2.secretDerivation(
            digest = SHA256,
            iterations = 100_000,
            outputSize = 32.bytes, // 256 bits = 32 bytes for AES-256
            salt = salt
        )
        val derivedKeyBytes = secretDerivation.deriveSecret(password.encodeToByteArray())

        // Decode the derived bytes as an AES key
        val aes = provider.get(AES.GCM)
        val keyDecoder = aes.keyDecoder()
        return keyDecoder.decodeFromByteArray(AES.Key.Format.RAW, derivedKeyBytes.toByteArray())
    }
}