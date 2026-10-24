/**
 * Copyright (C) 2025 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

import korlibs.io.file.VfsFile
import korlibs.io.file.std.MemoryVfsMix
import korlibs.io.file.std.asMemoryVfsFile
import korlibs.io.file.std.createZipFromTree
import kotlinx.coroutines.test.runTest
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import kotlin.test.*

class test_FileSystemKorio {

    private fun memoryVfs(): VfsFile = MemoryVfsMix(
        "a.txt" to "hello",
        "sub/b.txt" to "world"
    )

    private fun root(vfs: VfsFile = memoryVfs()) = DirectoryHandleKorio(FileSystemKorio, null, vfs)

    private suspend fun zipFile(): FileHandle {
        val zipBytes = memoryVfs().createZipFromTree()
        return FileHandleKorio(FileSystemKorio, null, zipBytes.asMemoryVfsFile("test.zip"))
    }

    // --- entry ---

    @Test
    fun entry_file() = runTest {
        assertIs<FileHandle>(root().entry("a.txt"))
    }

    @Test
    fun entry_directory() = runTest {
        assertIs<DirectoryHandle>(root().entry("sub"))
    }

    @Test
    fun entry_missing_isNull() = runTest {
        assertNull(root().entry("missing"))
    }

    // --- listContent ---

    @Test
    fun listContent_listsFilesAndDirectories() = runTest {
        val names = root().listContent().map { it.name }.toSet()
        assertEquals(setOf("a.txt", "sub"), names)
    }

    // --- read / write ---

    @Test
    fun readContent() = runTest {
        assertEquals("hello", root().file("a.txt")!!.readContent())
    }

    @Test
    fun readContent_nested() = runTest {
        assertEquals("world", root().directory("sub")!!.file("b.txt")!!.readContent())
    }

    @Test
    fun writeContent_thenRead() = runTest {
        val r = root()
        val f = r.file("a.txt")!!
        f.writeContent("changed")
        assertEquals("changed", r.file("a.txt")!!.readContent())
    }

    // --- exists ---

    @Test
    fun exists_true() = runTest {
        val r = root()
        assertTrue(r.exists())
        assertTrue(r.file("a.txt")!!.exists())
    }

    @Test
    fun exists_false() = runTest {
        val vfs = memoryVfs()
        val missing = DirectoryHandleKorio(FileSystemKorio, root(vfs), vfs["missing"])
        assertFalse(missing.exists())
    }

    // --- create via FileSystemKorio ---

    @Test
    fun createNewFile() = runTest {
        val r = root()
        val f = FileSystemKorio.createNewFile(r, "new.txt")
        assertNotNull(f)
        assertTrue(f.exists())
        assertEquals("", f.readContent())
        assertIs<FileHandle>(r.entry("new.txt"))
    }

    @Test
    fun createNewDirectory() = runTest {
        val r = root()
        val d = FileSystemKorio.createNewDirectory(r, "newDir")
        assertNotNull(d)
        assertTrue(d.exists())
        assertIs<DirectoryHandle>(r.entry("newDir"))
    }

    // --- create via handle ---

    @Test
    fun handle_createFile() = runTest {
        val r = root()
        val f = r.createFile("new.txt")
        assertNotNull(f)
        assertIs<FileHandle>(r.entry("new.txt"))
    }

    @Test
    fun handle_createDirectory() = runTest {
        val r = root()
        val d = r.createDirectory("newDir")
        assertNotNull(d)
        assertIs<DirectoryHandle>(r.entry("newDir"))
    }

    // --- path ---

    @Test
    fun path_directory() = runTest {
        val sub = root().directory("sub")!!
        assertTrue(sub.path.endsWith("/sub"), "path was '${sub.path}'")
    }

    @Test
    fun path_file() = runTest {
        val b = root().directory("sub")!!.file("b.txt")!!
        assertTrue(b.path.endsWith("/sub/b.txt"), "path was '${b.path}'")
    }

    // --- zip ---

    @Test
    fun openAsZipDirectory_listContent() = runTest {
        val zipRoot = zipFile().openAsZipDirectory()
        assertNotNull(zipRoot)
        val names = zipRoot.listContent().map { it.name }.toSet()
        assertEquals(setOf("a.txt", "sub"), names)
    }

    @Test
    fun openAsZipDirectory_readContent() = runTest {
        val zipRoot = zipFile().openAsZipDirectory()!!
        assertEquals("hello", zipRoot.file("a.txt")!!.readContent())
        assertEquals("world", zipRoot.directory("sub")!!.file("b.txt")!!.readContent())
    }

    @Test
    fun openAsZipDirectory_path() = runTest {
        val zipRoot = zipFile().openAsZipDirectory()!!
        val b = zipRoot.directory("sub")!!.file("b.txt")!!
        assertTrue(b.path.endsWith("/sub/b.txt"), "path was '${b.path}'")
    }

    // --- create when the name already exists ---

    @Test
    fun createFile_existingFile_keepsContent() = runTest {
        val r = root()
        val f = r.createFile("a.txt")
        assertNotNull(f)
        assertEquals("hello", f.readContent())
    }

    @Test
    fun createFile_whereDirectoryExists_isNull() = runTest {
        val r = root()
        assertNull(r.createFile("sub"))
        assertIs<DirectoryHandle>(r.entry("sub"))
    }

    @Test
    fun createDirectory_existingDirectory_returnsIt() = runTest {
        val r = root()
        val d = r.createDirectory("sub")
        assertNotNull(d)
        assertNotNull(d.file("b.txt"))
    }

    @Test
    fun createDirectory_whereFileExists_isNull() = runTest {
        val r = root()
        assertNull(r.createDirectory("a.txt"))
        assertEquals("hello", r.file("a.txt")!!.readContent())
    }
}
