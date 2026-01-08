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
import kotlin.test.*

// In-memory fake implementation of FileHandle
class FakeFileHandle(
    override val parent: DirectoryHandle?,
    override val name: String
) : FileHandle, FileHandleAbstract() {
    private var content: String? = null
    private var fileExists: Boolean = false

    override suspend fun readContent(): String? = content

    override suspend fun writeContent(content: String) {
        this.content = content
        this.fileExists = true
    }

    override suspend fun openAsZipDirectory() = null

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
            masterPassword = if (withMasterKey) "testPassword123" else null
        )
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
            masterPassword = "password"
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
}