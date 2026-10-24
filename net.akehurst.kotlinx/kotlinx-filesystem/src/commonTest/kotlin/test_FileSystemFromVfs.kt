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

import korlibs.io.file.std.MemoryVfsMix
import korlibs.io.file.std.createZipFromTree
import kotlinx.coroutines.test.runTest
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import kotlin.test.*

class test_FileSystemFromVfs {

    private fun fileSystem() = FileSystemFromVfs(
        MemoryVfsMix(
            "a.txt" to "hello",
            "sub/b.txt" to "world"
        )
    )

    // --- root ---

    @Test
    fun root_listContent() = runTest {
        val names = fileSystem().root.listContent().map { it.name }.toSet()
        assertEquals(setOf("a.txt", "sub"), names)
    }

    @Test
    fun root_entry() = runTest {
        val root = fileSystem().root
        assertIs<FileHandle>(root.entry("a.txt"))
        assertIs<DirectoryHandle>(root.entry("sub"))
        assertNull(root.entry("missing"))
    }

    // --- getFile / getDirectory / read ---

    @Test
    fun getFile_existing() = runTest {
        assertEquals("hello", fileSystem().getFile("a.txt")!!.readContent())
    }

    @Test
    fun getFile_nested() = runTest {
        assertEquals("world", fileSystem().getFile("sub/b.txt")!!.readContent())
    }

    @Test
    fun getFile_missing_isNull() = runTest {
        assertNull(fileSystem().getFile("missing.txt"))
    }

    @Test
    fun getFile_onDirectory_isNull() = runTest {
        assertNull(fileSystem().getFile("sub"))
    }

    @Test
    fun getDirectory_existing() = runTest {
        val sub = fileSystem().getDirectory("sub")
        assertNotNull(sub)
        assertEquals("world", sub.file("b.txt")!!.readContent())
    }

    @Test
    fun getDirectory_missing_isNull() = runTest {
        assertNull(fileSystem().getDirectory("missing"))
    }

    @Test
    fun getDirectory_onFile_isNull() = runTest {
        assertNull(fileSystem().getDirectory("a.txt"))
    }

    @Test
    fun read() = runTest {
        assertEquals("hello", fileSystem().read("a.txt"))
    }

    // --- write ---

    @Test
    fun writeContent_thenRead() = runTest {
        val fs = fileSystem()
        fs.getFile("a.txt")!!.writeContent("changed")
        assertEquals("changed", fs.read("a.txt"))
    }

    // --- create ---

    @Test
    fun createFile() = runTest {
        val fs = fileSystem()
        val f = fs.root.createFile("new.txt")
        assertNotNull(f)
        assertTrue(f.exists())
        assertEquals("", f.readContent())
    }

    @Test
    fun createDirectory() = runTest {
        val fs = fileSystem()
        val d = fs.root.createDirectory("newDir")
        assertNotNull(d)
        assertIs<DirectoryHandle>(fs.root.entry("newDir"))
    }

    // --- zip ---

    @Test
    fun openAsZipDirectory() = runTest {
        val zipBytes = MemoryVfsMix("a.txt" to "hello", "sub/b.txt" to "world").createZipFromTree()
        val fs = FileSystemFromVfs(MemoryVfsMix("test.zip" to zipBytes))
        val zipRoot = fs.getFile("test.zip")!!.openAsZipDirectory()
        assertNotNull(zipRoot)
        assertEquals("hello", zipRoot.file("a.txt")!!.readContent())
        assertEquals("world", zipRoot.directory("sub")!!.file("b.txt")!!.readContent())
    }
}
