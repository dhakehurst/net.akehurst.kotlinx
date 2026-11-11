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

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import net.akehurst.kotlinx.filesystem.FileHandleAbstract
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.utils.AnySerializer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.*

// In-memory fake implementation of FileHandle
class FakeFileHandle(
    override val parent: DirectoryHandle?,
    override val name: String
) : FileHandle, FileHandleAbstract() {
    private var content: String? = null
    private var fileExists: Boolean = false

    override suspend fun readContent(): String? = content

    override suspend fun readBytes(): ByteArray? = content?.encodeToByteArray()

    override suspend fun writeBytes(content: ByteArray) = writeContent(content.decodeToString())

    override suspend fun writeContent(content: String) {
        this.content = content
        this.fileExists = true
    }

    override suspend fun openAsZipDirectory(): DirectoryHandle = error("not a zip")

    override suspend fun exists(): Boolean = fileExists

    fun setExists(exists: Boolean) {
        fileExists = exists
    }

    fun getContent(): String? = content

    fun clear() {
        content = null
        fileExists = false
    }
}

fun fixedPassword(password: String?): MasterPasswordProvider = { _ -> password }

class test_SecureStorageDatastore {

    private lateinit var storageFile: FakeFileHandle
    private lateinit var masterKeyFile: FakeFileHandle

    @BeforeTest
    fun setup() {
        storageFile = FakeFileHandle(null, "storageFile")
        masterKeyFile = FakeFileHandle(null, "masterKeyFile")
    }

    private fun createDatastore(
        encrypt: Boolean = false,
        withMasterKey: Boolean = false
    ): SecureStorageDatastore {
        val serializersModule = SerializersModule {
            contextual(Any::class, AnySerializer())
            polymorphic(Any::class) {

            }
        }
        return SecureStorageDatastore(
            jsonSerializersModule = serializersModule,
            jsonClassDiscriminator = "type",
            secureStorageFile = storageFile,
            keyValueSeparator = "=",
            encrypt = encrypt,
            masterKeyFile = if (withMasterKey) masterKeyFile else null,
            masterPassword = if (withMasterKey) fixedPassword("testPassword123") else null
        )
    }

    @Test
    fun secretSource_reads_from_encrypted_storage() = runTest {
        val datastore = createDatastore(encrypt = true, withMasterKey = true)
        datastore.write("rm/david", "s3cret")
        val sut = SecretSourceSecureStorage("secure", datastore)

        assertEquals("s3cret", sut.read("rm/david"))
        assertNull(sut.read("other"))
    }

    // Happy path tests
    @Test
    fun testWriteAndReadString() = runTest {
        val datastore = createDatastore()

        datastore.write("testKey", "testValue")
        val result = datastore.read("testKey")

        assertEquals("testValue", result)
    }

    @Test
    fun testOverwriteExistingKey() = runTest {
        val datastore = createDatastore()

        datastore.write("key", "originalValue")
        datastore.write("key", "newValue")

        assertEquals("newValue", datastore.read("key"))
    }

    @Test
    fun testReadNonExistentKey() = runTest {
        val datastore = createDatastore()

        val result = datastore.read("nonExistent")

        assertNull(result)
    }

    @Test
    fun testDeleteKey() = runTest {
        val datastore = createDatastore()

        datastore.write("keyToDelete", "value")
        datastore.delete("keyToDelete")
        val result = datastore.read("keyToDelete")

        assertNull(result)
    }

    @Test
    fun testDeleteNonExistentKey() = runTest {
        val datastore = createDatastore()

        // Should not throw
        datastore.delete("nonExistent")
        assertNull(datastore.read("nonExistent"))
    }

    @Test
    fun testClearAllData() = runTest {
        val datastore = createDatastore()

        datastore.write("key1", "value1")
        datastore.write("key2", "value2")
        datastore.write("key3", "value3")

        datastore.clear()

        assertNull(datastore.read("key1"))
        assertNull(datastore.read("key2"))
        assertNull(datastore.read("key3"))
    }

    @Test
    fun testClearAndRewrite() = runTest {
        val datastore = createDatastore()

        datastore.write("key1", "value1")
        datastore.clear()
        datastore.write("key2", "value2")

        assertNull(datastore.read("key1"))
        assertEquals("value2", datastore.read("key2"))
    }

    // File persistence tests
    @Test
    fun testLoadFromFile() = runTest {
        val datastore = createDatastore()
        storageFile.writeContent("persistedKey=\"persistedValue\"\n")

        val result = datastore.read("persistedKey")

        assertEquals("persistedValue", result)
    }

    @Test
    fun testFileContentPersistence() = runTest {
        val datastore = createDatastore()

        datastore.write("key1", "value1")
        datastore.write("key2", "value2")

        val fileContent = storageFile.getContent()
        assertNotNull(fileContent)
        assertTrue(fileContent.contains("key1="))
        assertTrue(fileContent.contains("key2="))
    }

    @Test
    fun testEmptyFileReturnsNull() = runTest {
        val datastore = createDatastore()
        storageFile.writeContent("")

        val result = datastore.read("anyKey")

        assertNull(result)
    }

    @Test
    fun testIgnoreBlankLines() = runTest {
        val datastore = createDatastore()
        storageFile.writeContent("key1=\"value1\"\n\n\nkey2=\"value2\"\n")

        assertEquals("value1", datastore.read("key1"))
        assertEquals("value2", datastore.read("key2"))
    }

    @Test
    fun testMultipleReads() = runTest {
        val datastore = createDatastore()
        storageFile.writeContent("key=\"value\"\n")

        // First read loads from file
        val result1 = datastore.read("key")
        // Second read uses cached state
        val result2 = datastore.read("key")

        assertEquals("value", result1)
        assertEquals("value", result2)
    }

    // Encryption tests
    @Test
    fun testWriteWithoutEncryption() = runTest {
        val datastore = createDatastore(encrypt = false)

        datastore.write("plainKey", "plainValue")
        val result = datastore.read("plainKey")

        assertEquals("plainValue", result)
    }

    @Test
    fun testWriteWithEncryptionRequiresMasterKey() = runTest {
        val datastore = createDatastore(encrypt = true, withMasterKey = false)

        assertFailsWith<IllegalStateException> {
            datastore.write("key", "value")
        }
    }

    @Test
    fun testReadWithEncryptionRequiresMasterKey() = runTest {
        val datastore = createDatastore(encrypt = true, withMasterKey = false)
        storageFile.writeContent("key=encryptedValue\n")

        assertFailsWith<IllegalStateException> {
            datastore.read("key")
        }
    }

    @Test
    fun testMissingMasterPasswordThrowsError() = runTest {
        val serializersModule = SerializersModule {
            contextual(Any::class, AnySerializer())
        }

        val datastore = SecureStorageDatastore(
            jsonSerializersModule = serializersModule,
            jsonClassDiscriminator = "type",
            secureStorageFile = storageFile,
            keyValueSeparator = "=",
            encrypt = true,
            masterKeyFile = masterKeyFile,
            masterPassword = null
        )

        assertFailsWith<IllegalStateException> {
            datastore.write("key", "value")
        }
    }

    @Test
    fun testMissingMasterKeyFileThrowsError() = runTest {
        val serializersModule = SerializersModule {
            contextual(Any::class, AnySerializer())
        }

        val datastore = SecureStorageDatastore(
            jsonSerializersModule = serializersModule,
            jsonClassDiscriminator = "type",
            secureStorageFile = storageFile,
            keyValueSeparator = "=",
            encrypt = true,
            masterKeyFile = null,
            masterPassword = { _ -> "password" }
        )

        assertFailsWith<IllegalStateException> {
            datastore.write("key", "value")
        }
    }

    @Test
    fun testEncryptionWithValidMasterKey() = runTest {
        val datastore = createDatastore(encrypt = true, withMasterKey = true)
        masterKeyFile.setExists(false)

        datastore.write("secretKey", "secretValue")
        val result = datastore.read("secretKey")

        assertEquals("secretValue", result)
    }


    // Edge cases and error conditions
    @Test
    fun testEmptyStringValue() = runTest {
        val datastore = createDatastore()

        datastore.write("emptyKey", "")
        val result = datastore.read("emptyKey")

        assertEquals("", result)
    }

    @Test
    fun testKeyWithSpecialCharacters() = runTest {
        val datastore = createDatastore()

        datastore.write("key-with-dashes", "value")
        val result = datastore.read("key-with-dashes")

        assertEquals("value", result)
    }

    @Test
    fun testValueWithEqualsSign() = runTest {
        val datastore = createDatastore()

        // Note: This might fail if the value contains the separator
        // depending on the split implementation
        datastore.write("key", "value=withEquals")
        val result = datastore.read("key")

        // The deserialization should handle this correctly
        assertEquals("value=withEquals", result)
    }

    @Test
    fun testLargeStringValue() = runTest {
        val datastore = createDatastore()
        val largeValue = "a".repeat(10000)

        datastore.write("largeKey", largeValue)
        val result = datastore.read("largeKey")

        assertEquals(largeValue, result)
    }

    @Test
    fun testClearEmptyDatastore() = runTest {
        val datastore = createDatastore()

        // Should not throw
        datastore.clear()
        assertNull(datastore.read("anyKey"))
    }


    // State and concurrency-related tests
    @Test
    fun testSequentialOperations() = runTest {
        val datastore = createDatastore()

        datastore.write("key1", "value1")
        datastore.write("key2", "value2")
        datastore.delete("key1")
        datastore.write("key3", "value3")

        assertNull(datastore.read("key1"))
        assertEquals("value2", datastore.read("key2"))
        assertEquals("value3", datastore.read("key3"))
    }

    @Test
    fun testReadBeforeFirstWrite() = runTest {
        val datastore = createDatastore()

        val result = datastore.read("nonExistent")

        assertNull(result)
    }

    @Test
    fun testWriteAfterClear() = runTest {
        val datastore = createDatastore()

        datastore.write("key1", "value1")
        datastore.clear()
        datastore.write("key2", "value2")

        assertNull(datastore.read("key1"))
        assertEquals("value2", datastore.read("key2"))
    }

    @Test
    fun testDeleteAfterClear() = runTest {
        val datastore = createDatastore()

        datastore.write("key", "value")
        datastore.clear()
        datastore.delete("key") // Should not throw

        assertNull(datastore.read("key"))
    }

    // ---- master password, key file, wrong password ----

    private fun encryptedDatastore(
        password: MasterPasswordProvider,
        keyFile: FakeFileHandle = masterKeyFile,
        file: FakeFileHandle = storageFile,
    ) = SecureStorageDatastore(
        secureStorageFile = file,
        encrypt = true,
        masterKeyFile = keyFile,
        masterPassword = password,
    )

    @Test
    fun roundTrip_with_password_and_no_serializers_module() = runTest {
        encryptedDatastore(fixedPassword("pw1")).write("token", "abc=123")

        // a new instance (new session) reads the files written by the first
        val sut = encryptedDatastore(fixedPassword("pw1"))
        assertFalse(sut.isUnlocked)
        assertEquals("abc=123", sut.read("token"))
        assertEquals("abc=123", sut.readString("token"))
        assertTrue(sut.isUnlocked)
        // the value is not stored in plain text
        assertFalse(storageFile.getContent()!!.contains("abc=123"))
    }

    @Test
    fun password_is_asked_lazily_once_and_isNew_says_if_key_file_exists() = runTest {
        val calls = mutableListOf<Boolean>()
        val first = encryptedDatastore({ isNew -> calls.add(isNew); "pw" })
        assertEquals(emptyList(), calls) // not asked at construction
        assertEquals(emptySet(), first.keys()) // keys need no password
        assertEquals(emptyList(), calls)
        first.write("a", "1")
        first.write("b", "2")
        assertEquals("1", first.read("a"))
        assertEquals(listOf(true), calls)

        val second = encryptedDatastore({ isNew -> calls.add(isNew); "pw" })
        assertEquals("2", second.read("b"))
        assertEquals(listOf(true, false), calls)
    }

    @Test
    fun wrong_password_gives_WrongMasterPasswordException_and_can_retry() = runTest {
        encryptedDatastore(fixedPassword("right")).write("token", "secret")

        val passwords = mutableListOf("wrong", "right")
        val sut = encryptedDatastore({ _ -> passwords.removeAt(0) })
        assertFailsWith<WrongMasterPasswordException> { sut.read("token") }
        assertFalse(sut.isUnlocked)
        assertEquals("secret", sut.read("token"))
    }

    @Test
    fun cancelled_password_gives_MasterPasswordCancelledException() = runTest {
        val sut = encryptedDatastore(fixedPassword(null))
        assertFailsWith<MasterPasswordCancelledException> { sut.write("token", "secret") }
        assertNull(storageFile.getContent())
    }

    @Test
    fun write_before_read_keeps_existing_entries() = runTest {
        val first = encryptedDatastore(fixedPassword("pw"))
        first.write("a", "1")
        first.write("b", "2")

        val second = encryptedDatastore(fixedPassword("pw"))
        second.write("c", "3") // no read first
        assertEquals(setOf("a", "b", "c"), second.keys())

        val third = encryptedDatastore(fixedPassword("pw"))
        assertEquals("1", third.read("a"))
        assertEquals("2", third.read("b"))
        assertEquals("3", third.read("c"))
    }

    @Test
    fun remove_before_read_keeps_other_entries() = runTest {
        val first = createDatastore()
        first.write("a", "1")
        first.write("b", "2")

        val second = createDatastore()
        second.remove("a")
        assertEquals(setOf("b"), createDatastore().keys())
    }

    @Test
    fun concurrent_writes_keep_all_entries() = runTest {
        val sut = encryptedDatastore(fixedPassword("pw"))
        coroutineScope {
            (1..20).map { i -> async { sut.write("k$i", "v$i") } }.awaitAll()
        }
        val reread = encryptedDatastore(fixedPassword("pw"))
        assertEquals((1..20).map { "k$it" }.toSet(), reread.keys())
        assertEquals("v7", reread.read("k7"))
    }

    @Test
    fun salt_differs_between_two_createKey_calls() = runTest {
        val f1 = FakeFileHandle(null, "k1")
        val f2 = FakeFileHandle(null, "k2")
        Encryption().createKey(f1, "same password")
        Encryption().createKey(f2, "same password")
        val j1 = Json.parseToJsonElement(f1.getContent()!!).jsonObject
        val j2 = Json.parseToJsonElement(f2.getContent()!!).jsonObject
        assertEquals(1, j1["version"]!!.jsonPrimitive.int)
        val salt1 = Base64.decode(j1["salt"]!!.jsonPrimitive.content)
        val salt2 = Base64.decode(j2["salt"]!!.jsonPrimitive.content)
        assertTrue(salt1.size >= 16)
        assertFalse(salt1.contentEquals(salt2))
        assertNotEquals(j1["key"], j2["key"])
    }

    @Test
    fun loadKey_with_wrong_password_throws_WrongMasterPasswordException() = runTest {
        Encryption().createKey(masterKeyFile, "right")
        assertFailsWith<WrongMasterPasswordException> { Encryption().loadKey(masterKeyFile, "wrong") }
        val e = Encryption()
        e.loadKey(masterKeyFile, "right")
        assertTrue(e.isLoaded)
    }

    @Test
    fun reset_deletes_key_and_values_and_asks_for_a_new_password() = runTest {
        val sut = encryptedDatastore(fixedPassword("forgotten"))
        sut.write("token", "secret")
        assertTrue(sut.isUnlocked)

        sut.reset()
        assertFalse(sut.isUnlocked)
        assertTrue(masterKeyFile.getContent().isNullOrEmpty())
        assertTrue(storageFile.getContent().isNullOrEmpty())
        assertEquals(emptySet(), sut.keys())

        val calls = mutableListOf<Boolean>()
        val fresh = encryptedDatastore({ isNew -> calls.add(isNew); "new" })
        assertEquals(emptySet(), fresh.keys())
        fresh.write("token", "secret2")
        assertEquals(listOf(true), calls)
        assertEquals("secret2", encryptedDatastore(fixedPassword("new")).read("token"))
    }

    @Test
    fun string_values_work_without_serializers_module_unencrypted() = runTest {
        val sut = SecureStorageDatastore(secureStorageFile = storageFile, encrypt = false)
        sut.write("k", "v=1")
        assertEquals("v=1", SecureStorageDatastore(secureStorageFile = storageFile, encrypt = false).read("k"))
    }
}
