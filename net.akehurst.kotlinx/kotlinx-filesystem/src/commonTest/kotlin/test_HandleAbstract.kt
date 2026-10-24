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

import kotlinx.coroutines.test.runTest
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.filesystem.api.FileSystemObjectHandle
import kotlin.test.*

class test_HandleAbstract {

    private class FakeDir(
        override val parent: FakeDir?,
        override val name: String
    ) : DirectoryHandleAbstract() {
        val entries = mutableMapOf<String, FileSystemObjectHandle>()

        fun addDir(name: String): FakeDir = FakeDir(this, name).also { entries[name] = it }
        fun addFile(name: String): FakeFile = FakeFile(this, name).also { entries[name] = it }

        override suspend fun exists(): Boolean = true
        override suspend fun listContent(): List<FileSystemObjectHandle> = entries.values.toList()
        override suspend fun entry(name: String): FileSystemObjectHandle? = entries[name]
        override suspend fun createFile(name: String): FileHandle? = addFile(name)
        override suspend fun createDirectory(name: String): DirectoryHandle? = addDir(name)
    }

    private class FakeFile(
        override val parent: FakeDir?,
        override val name: String
    ) : FileHandleAbstract() {
        override suspend fun exists(): Boolean = true
        override suspend fun readContent(): String? = null
        override suspend fun writeContent(content: String) {}
        override suspend fun openAsZipDirectory(): DirectoryHandle? = null
    }

    // --- extension ---

    @Test
    fun extension_simple() {
        assertEquals("txt", FakeFile(null, "a.txt").extension)
    }

    @Test
    fun extension_multipleDots_isLastPart() {
        assertEquals("gz", FakeFile(null, "archive.tar.gz").extension)
    }

    @Test
    fun extension_noDot_isEmpty() {
        assertEquals("", FakeFile(null, "Makefile").extension)
    }

    // --- nameWithoutExtension ---

    @Test
    fun nameWithoutExtension_simple() {
        assertEquals("a", FakeFile(null, "a.txt").nameWithoutExtension)
    }

    @Test
    fun nameWithoutExtension_multipleDots_removesLastPartOnly() {
        assertEquals("archive.tar", FakeFile(null, "archive.tar.gz").nameWithoutExtension)
    }

    @Test
    fun nameWithoutExtension_noDot_isWholeName() {
        assertEquals("Makefile", FakeFile(null, "Makefile").nameWithoutExtension)
    }

    @Test
    fun nameWithoutExtension_dotFile_isEmpty() {
        val f = FakeFile(null, ".bashrc")
        assertEquals("", f.nameWithoutExtension)
        assertEquals("bashrc", f.extension)
    }

    // --- path ---

    @Test
    fun path_directory_isParentPathPlusName() {
        val root = FakeDir(null, "root")
        val sub = root.addDir("sub")
        assertEquals(root.path + "/sub", sub.path)
    }

    @Test
    fun path_file_isParentPathPlusName() {
        val root = FakeDir(null, "root")
        val f = root.addDir("sub").addFile("f.txt")
        assertEquals(f.parent!!.path + "/f.txt", f.path)
    }

    @Test
    fun absolutePath_defaultsToPath() {
        val root = FakeDir(null, "root")
        val sub = root.addDir("sub")
        val f = sub.addFile("f.txt")
        assertEquals(sub.path, sub.absolutePath)
        assertEquals(f.path, f.absolutePath)
    }

    // --- file() / directory() ---

    @Test
    fun file_returnsFileEntry() = runTest {
        val root = FakeDir(null, "root")
        val f = root.addFile("f.txt")
        assertSame(f, root.file("f.txt"))
    }

    @Test
    fun file_returnsNullForDirectoryEntry() = runTest {
        val root = FakeDir(null, "root")
        root.addDir("sub")
        assertNull(root.file("sub"))
    }

    @Test
    fun file_returnsNullForMissingEntry() = runTest {
        val root = FakeDir(null, "root")
        assertNull(root.file("missing"))
    }

    @Test
    fun directory_returnsDirectoryEntry() = runTest {
        val root = FakeDir(null, "root")
        val sub = root.addDir("sub")
        assertSame(sub, root.directory("sub"))
    }

    @Test
    fun directory_returnsNullForFileEntry() = runTest {
        val root = FakeDir(null, "root")
        root.addFile("f.txt")
        assertNull(root.directory("f.txt"))
    }

    @Test
    fun directory_returnsNullForMissingEntry() = runTest {
        val root = FakeDir(null, "root")
        assertNull(root.directory("missing"))
    }

    // --- getOrCreateFile() / getOrCreateDirectory() ---

    @Test
    fun getOrCreateFile_returnsExistingFile() = runTest {
        val root = FakeDir(null, "root")
        val f = root.addFile("f.txt")
        assertSame(f, root.getOrCreateFile("f.txt"))
        assertEquals(1, root.entries.size)
    }

    @Test
    fun getOrCreateFile_createsMissingFile() = runTest {
        val root = FakeDir(null, "root")
        val f = root.getOrCreateFile("new.txt")
        assertNotNull(f)
        assertSame(f, root.entries["new.txt"])
    }

    @Test
    fun getOrCreateFile_returnsNullForDirectoryEntry() = runTest {
        val root = FakeDir(null, "root")
        val sub = root.addDir("sub")
        assertNull(root.getOrCreateFile("sub"))
        assertSame(sub, root.entries["sub"])
    }

    @Test
    fun getOrCreateDirectory_returnsExistingDirectory() = runTest {
        val root = FakeDir(null, "root")
        val sub = root.addDir("sub")
        assertSame(sub, root.getOrCreateDirectory("sub"))
        assertEquals(1, root.entries.size)
    }

    @Test
    fun getOrCreateDirectory_createsMissingDirectory() = runTest {
        val root = FakeDir(null, "root")
        val sub = root.getOrCreateDirectory("newDir")
        assertNotNull(sub)
        assertSame(sub, root.entries["newDir"])
    }

    @Test
    fun getOrCreateDirectory_returnsNullForFileEntry() = runTest {
        val root = FakeDir(null, "root")
        val f = root.addFile("f.txt")
        assertNull(root.getOrCreateDirectory("f.txt"))
        assertSame(f, root.entries["f.txt"])
    }
}
