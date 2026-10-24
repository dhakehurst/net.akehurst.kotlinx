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
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class test_UserFileSystemDefault {

    private lateinit var tmp: File

    @BeforeTest
    fun setUp() {
        tmp = Files.createTempDirectory("kotlinx-filesystem-test").toFile()
        File(tmp, "a.txt").writeText("hello")
        File(tmp, "sub").mkdir()
        File(tmp, "sub/b.txt").writeText("world")
    }

    @AfterTest
    fun tearDown() {
        tmp.deleteRecursively()
    }

    private suspend fun root(): DirectoryHandle = UserFileSystemDefault.getDirectory(tmp.absolutePath)!!

    private fun writeZip(): File {
        val zip = File(tmp, "test.zip")
        ZipOutputStream(zip.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("a.txt"))
            zos.write("hello".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("sub/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("sub/b.txt"))
            zos.write("world".toByteArray())
            zos.closeEntry()
        }
        return zip
    }

    // --- getDirectory ---

    @Test
    fun getDirectory_existing() = runTest {
        val dir = UserFileSystemDefault.getDirectory(tmp.absolutePath)
        assertNotNull(dir)
        assertEquals(tmp.absolutePath, dir.absolutePath)
    }

    @Test
    fun getDirectory_missing_isNull() = runTest {
        assertNull(UserFileSystemDefault.getDirectory(File(tmp, "missing").absolutePath))
    }

    @Test
    fun getDirectory_onFile_isNull() = runTest {
        assertNull(UserFileSystemDefault.getDirectory(File(tmp, "a.txt").absolutePath))
    }

    // --- getEntry ---

    @Test
    fun getEntry_file() = runTest {
        assertIs<FileHandle>(UserFileSystemDefault.getEntry(root(), "a.txt"))
    }

    @Test
    fun getEntry_directory() = runTest {
        assertIs<DirectoryHandle>(UserFileSystemDefault.getEntry(root(), "sub"))
    }

    @Test
    fun getEntry_missing_isNull() = runTest {
        assertNull(UserFileSystemDefault.getEntry(root(), "missing"))
    }

    @Test
    fun getEntry_emptyName_isParent() = runTest {
        val r = root()
        assertSame(r, UserFileSystemDefault.getEntry(r, ""))
    }

    // --- listDirectoryContent ---

    @Test
    fun listDirectoryContent_listsFilesAndDirectories() = runTest {
        val names = UserFileSystemDefault.listDirectoryContent(root()).map { it.name }.toSet()
        assertEquals(setOf("a.txt", "sub"), names)
    }

    @Test
    fun listDirectoryContent_withBrokenSymlink_stillListsOtherEntries() = runTest {
        // symbolic link creation may need extra privileges on Windows
        Files.createSymbolicLink(File(tmp, "broken").toPath(), File(tmp, "does-not-exist").toPath())
        val names = UserFileSystemDefault.listDirectoryContent(root()).map { it.name }.toSet()
        assertTrue(names.containsAll(setOf("a.txt", "sub")), "listed: $names")
    }

    // --- read / write ---

    @Test
    fun readFileContent() = runTest {
        assertEquals("hello", root().file("a.txt")!!.readContent())
    }

    @Test
    fun readFileContent_nested() = runTest {
        assertEquals("world", root().directory("sub")!!.file("b.txt")!!.readContent())
    }

    @Test
    fun writeFileContent_thenRead() = runTest {
        val f = root().file("a.txt")!!
        f.writeContent("changed")
        assertEquals("changed", File(tmp, "a.txt").readText())
        assertEquals("changed", f.readContent())
    }

    // --- create ---

    @Test
    fun createNewFile() = runTest {
        val f = root().createFile("new.txt")
        assertNotNull(f)
        assertTrue(File(tmp, "new.txt").isFile)
        assertTrue(f.exists())
    }

    @Test
    fun createNewDirectory() = runTest {
        val d = root().createDirectory("newDir")
        assertNotNull(d)
        assertTrue(File(tmp, "newDir").isDirectory)
        assertTrue(d.exists())
    }

    // --- exists ---

    @Test
    fun exists_true() = runTest {
        val r = root()
        assertTrue(r.exists())
        assertTrue(r.file("a.txt")!!.exists())
    }

    @Test
    fun exists_falseAfterDelete() = runTest {
        val f = root().file("a.txt")!!
        File(tmp, "a.txt").delete()
        assertFalse(f.exists())
    }

    // --- absolutePath ---

    @Test
    fun directoryHandle_absolutePath_isAbsolute() {
        val d = DirectoryHandleJVM(UserFileSystemDefault, null, File("relativeDir"))
        assertEquals(File("relativeDir").absolutePath, d.absolutePath)
    }

    @Test
    fun fileHandle_absolutePath_isAbsolute() {
        val f = FileHandleJVM(UserFileSystemDefault, null, File("relative.txt"))
        assertEquals(File("relative.txt").absolutePath, f.absolutePath)
    }

    // --- zip ---

    @Test
    fun openFileAsZipDirectory_listContent() = runTest {
        writeZip()
        val zipRoot = root().file("test.zip")!!.openAsZipDirectory()
        assertNotNull(zipRoot)
        val names = zipRoot.listContent().map { it.name }.toSet()
        assertEquals(setOf("a.txt", "sub"), names)
    }

    @Test
    fun openFileAsZipDirectory_readContent() = runTest {
        writeZip()
        val zipRoot = root().file("test.zip")!!.openAsZipDirectory()!!
        assertEquals("hello", zipRoot.file("a.txt")!!.readContent())
        assertEquals("world", zipRoot.directory("sub")!!.file("b.txt")!!.readContent())
    }

    @Test
    fun openFileAsZipDirectory_path() = runTest {
        writeZip()
        val zipRoot = root().file("test.zip")!!.openAsZipDirectory()!!
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
