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
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.filesystem.api.ZipErrorReason
import net.akehurst.kotlinx.filesystem.api.ZipException
import net.akehurst.kotlinx.filesystem.api.walkFiles
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.*
import kotlin.time.Instant

/**
 * zip archives written by java.util.zip, as produced by common tools, and files on disk
 */
class test_ZipFileSystemJvm {

    private lateinit var tmp: File

    @BeforeTest
    fun setUp() {
        tmp = Files.createTempDirectory("kotlinx-filesystem-zip-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tmp.deleteRecursively()
    }

    private fun javaZip(charset: Charset = Charsets.UTF_8, block: ZipOutputStream.() -> Unit): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos, charset).use { it.block() }
        return bos.toByteArray()
    }

    private fun ZipOutputStream.entry(name: String, content: ByteArray, method: Int = ZipEntry.DEFLATED, time: FileTime? = null) {
        val e = ZipEntry(name)
        e.method = method
        if (method == ZipEntry.STORED) {
            e.size = content.size.toLong()
            e.crc = java.util.zip.CRC32().apply { update(content) }.value
        }
        if (time != null) e.lastModifiedTime = time
        putNextEntry(e)
        write(content)
        closeEntry()
    }

    @Test
    fun javaZip_deflatedWithDataDescriptor_matchesJavaZipFile() = runTest {
        val content = "fictional line of text\n".repeat(1000).encodeToByteArray()
        val bytes = javaZip {
            entry("dir/text.txt", content) // DEFLATED entries are written with a data descriptor
            entry("stored.bin", byteArrayOf(1, 2, 3), ZipEntry.STORED)
        }
        val file = File(tmp, "j.zip").apply { writeBytes(bytes) }
        val root = ZipFileSystem.open(bytes)
        val text = root.entry("dir/text.txt") as FileHandle
        assertContentEquals(content, text.readBytes())
        ZipFile(file).use { jz ->
            val je = jz.getEntry("dir/text.txt")
            val md = text.metadata()
            assertEquals(je.size, md.size)
            assertEquals(je.compressedSize, md.compressedSize)
            assertEquals(je.crc, md.crc32)
        }
        assertContentEquals(byteArrayOf(1, 2, 3), root.file("stored.bin")!!.readBytes())
    }

    @Test
    fun javaZip_lastModified_extendedTimestamp() = runTest {
        val time = Instant.parse("2023-06-01T10:20:30Z")
        val bytes = javaZip { entry("t.txt", "x".encodeToByteArray(), time = FileTime.fromMillis(time.toEpochMilliseconds())) }
        assertEquals(time, ZipFileSystem.open(bytes).file("t.txt")!!.metadata().lastModified)
    }

    @Test
    fun javaZip_cp437Names() = runTest {
        val bytes = javaZip(Charset.forName("IBM437")) { entry("Größe.txt", "cp437".encodeToByteArray()) }
        assertEquals(listOf("Größe.txt"), ZipFileSystem.open(bytes).listContent().map { it.name })
    }

    @Test
    fun javaZip_utf8Names() = runTest {
        val bytes = javaZip { entry("Ελληνικά/中文.txt", "utf8".encodeToByteArray()) }
        assertEquals(listOf("Ελληνικά/中文.txt"), ZipFileSystem.open(bytes).walkFiles().map { it.relativePath })
    }

    @Test
    fun javaZip_moreThan65535Entries_zip64() = runTest {
        val count = 70_000
        val bytes = javaZip {
            for (i in 0 until count) entry("f/$i.txt", ByteArray(0), ZipEntry.STORED)
        }
        val root = ZipFileSystem.open(bytes)
        val files = root.directory("f")!!.listContent()
        assertEquals(count, files.size)
        assertEquals("69999.txt", files.last().name)
    }

    @Test
    fun builder_moreThan65535Entries_zip64() = runTest {
        val b = ZipTestBuilder(zip64 = true)
        for (i in 0 until 66_000) b.stored("e$i", "$i")
        val root = ZipFileSystem.open(b.build())
        assertEquals(66_000, root.listContent().size)
        assertEquals("65999", root.file("e65999")!!.readContent())
    }

    // --- files on disk ---

    @Test
    fun openFileAsZipDirectory_fromFile() = runTest {
        File(tmp, "a.zip").writeBytes(javaZip { entry("x.txt", "from disk".encodeToByteArray()) })
        val dir = UserFileSystemDefault.getDirectory(tmp.absolutePath)!!
        val zipRoot = dir.file("a.zip")!!.openAsZipDirectory()
        assertEquals("a.zip", zipRoot.name)
        assertEquals("from disk", zipRoot.file("x.txt")!!.readContent())
    }

    @Test
    fun openFileAsZipDirectory_notAZip_throws() = runTest {
        File(tmp, "not.zip").writeText("plain text")
        val dir = UserFileSystemDefault.getDirectory(tmp.absolutePath)!!
        val e = assertFailsWith<ZipException> { dir.file("not.zip")!!.openAsZipDirectory() }
        assertEquals(ZipErrorReason.NOT_A_ZIP, e.reason)
    }

    @Test
    fun metadata_fileOnDisk() = runTest {
        val f = File(tmp, "m.txt").apply { writeText("12345") }
        val time = Instant.parse("2022-01-02T03:04:05Z")
        f.setLastModified(time.toEpochMilliseconds())
        val md = UserFileSystemDefault.getDirectory(tmp.absolutePath)!!.file("m.txt")!!.metadata()
        assertEquals(5L, md.size)
        assertEquals(time, md.lastModified)
        assertNull(md.compressedSize)
        assertNull(md.crc32)
    }
}
