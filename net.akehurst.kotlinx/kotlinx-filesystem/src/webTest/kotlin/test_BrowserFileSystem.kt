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

package net.akehurst.kotlinx.filesystem

import kotlinx.coroutines.test.runTest
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import kotlin.random.Random
import kotlin.test.*

/**
 * UserFileSystemDefault on JS and Wasm, run against the browser's origin private file system.
 * The dialogs cannot be tested this way; everything reached through the handles can.
 */
class test_BrowserFileSystem {

    /**
     * a new empty directory for one test, holding a.txt ("hello") and sub/b.txt ("world");
     * null when not running in a browser
     */
    private suspend fun testDir(): DirectoryHandle? {
        val root = browserTestRoot() ?: return null
        val dir = root.createDirectory("test-" + Random.nextLong().toULong().toString(16))!!
        dir.createFile("a.txt")!!.writeContent("hello")
        dir.createDirectory("sub")!!.createFile("b.txt")!!.writeContent("world")
        return dir
    }

    // --- entry / file / directory ---

    @Test
    fun entry_kinds() = runTest {
        val d = testDir() ?: return@runTest
        assertIs<FileHandle>(d.entry("a.txt"))
        assertIs<DirectoryHandle>(d.entry("sub"))
        assertNull(d.entry("missing"))
    }

    @Test
    fun file_and_directory_wrongKind_isNull() = runTest {
        val d = testDir() ?: return@runTest
        assertNull(d.file("sub"))
        assertNull(d.directory("a.txt"))
    }

    @Test
    fun listContent() = runTest {
        val d = testDir() ?: return@runTest
        assertEquals(setOf("a.txt", "sub"), d.listContent().map { it.name }.toSet())
    }

    @Test
    fun exists() = runTest {
        val d = testDir() ?: return@runTest
        assertTrue(d.exists())
        assertTrue(d.file("a.txt")!!.exists())
    }

    @Test
    fun path_endsWithParentAndName() = runTest {
        val d = testDir() ?: return@runTest
        val b = d.directory("sub")!!.file("b.txt")!!
        assertTrue(b.path.endsWith("/sub/b.txt"), "path was '${b.path}'")
    }

    // --- read / write ---

    @Test
    fun readContent() = runTest {
        val d = testDir() ?: return@runTest
        assertEquals("hello", d.file("a.txt")!!.readContent())
        assertEquals("world", d.directory("sub")!!.file("b.txt")!!.readContent())
    }

    @Test
    fun writeContent_replacesContent() = runTest {
        val d = testDir() ?: return@runTest
        val a = d.file("a.txt")!!
        a.writeContent("changed")
        assertEquals("changed", a.readContent())
    }

    // --- create ---

    @Test
    fun createFile_new_isEmpty() = runTest {
        val d = testDir() ?: return@runTest
        val f = d.createFile("new.txt")
        assertNotNull(f)
        assertEquals("", f.readContent())
        assertIs<FileHandle>(d.entry("new.txt"))
    }

    @Test
    fun createFile_existingFile_keepsContent() = runTest {
        val d = testDir() ?: return@runTest
        val f = d.createFile("a.txt")
        assertNotNull(f)
        assertEquals("hello", f.readContent())
    }

    @Test
    fun createFile_whereDirectoryExists_isNull() = runTest {
        val d = testDir() ?: return@runTest
        assertNull(d.createFile("sub"))
        assertIs<DirectoryHandle>(d.entry("sub"))
    }

    @Test
    fun createDirectory_new() = runTest {
        val d = testDir() ?: return@runTest
        assertNotNull(d.createDirectory("newDir"))
        assertIs<DirectoryHandle>(d.entry("newDir"))
    }

    @Test
    fun createDirectory_existingDirectory_returnsIt() = runTest {
        val d = testDir() ?: return@runTest
        val sub = d.createDirectory("sub")
        assertNotNull(sub)
        assertNotNull(sub.file("b.txt"))
    }

    @Test
    fun createDirectory_whereFileExists_isNull() = runTest {
        val d = testDir() ?: return@runTest
        assertNull(d.createDirectory("a.txt"))
        assertEquals("hello", d.file("a.txt")!!.readContent())
    }

    // --- getOrCreate ---

    @Test
    fun getOrCreateFile() = runTest {
        val d = testDir() ?: return@runTest
        assertEquals("hello", d.getOrCreateFile("a.txt")!!.readContent())
        assertNotNull(d.getOrCreateFile("new.txt"))
        assertIs<FileHandle>(d.entry("new.txt"))
        assertNull(d.getOrCreateFile("sub"))
    }

    @Test
    fun getOrCreateDirectory() = runTest {
        val d = testDir() ?: return@runTest
        assertNotNull(d.getOrCreateDirectory("sub")!!.file("b.txt"))
        assertNotNull(d.getOrCreateDirectory("newDir"))
        assertIs<DirectoryHandle>(d.entry("newDir"))
        assertNull(d.getOrCreateDirectory("a.txt"))
    }
}
