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
import net.akehurst.kotlinx.filesystem.UserFileSystemDefault
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class test_SecureStorageDatastore_jvm {

    @Test
    fun files_are_owner_only_and_reset_deletes_them() = runTest {
        if ("posix" !in FileSystems.getDefault().supportedFileAttributeViews()) return@runTest
        val dir = Files.createTempDirectory("secure-storage-test")
        val dirHandle = UserFileSystemDefault.getDirectory(dir.toString())!!
        val sut = SecureStorageDatastore(
            secureStorageFile = dirHandle.getOrCreateFile("secrets")!!,
            encrypt = true,
            masterKeyFile = dirHandle.getOrCreateFile("secrets.key")!!,
            masterPassword = { _ -> "pw" },
        )
        sut.write("token", "abc")

        for (name in listOf("secrets", "secrets.key")) {
            val perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve(name)))
            assertEquals("rw-------", perms, name)
        }

        sut.reset()
        assertFalse(Files.exists(dir.resolve("secrets")))
        assertFalse(Files.exists(dir.resolve("secrets.key")))

        // usable again after reset, with a new password
        sut.write("token", "def")
        assertTrue(Files.exists(dir.resolve("secrets.key")))
        assertEquals("def", sut.read("token"))
    }
}
