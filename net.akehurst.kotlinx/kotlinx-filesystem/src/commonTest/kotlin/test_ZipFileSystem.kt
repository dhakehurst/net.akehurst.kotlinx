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
import net.akehurst.kotlinx.filesystem.api.ReadOnlyFileSystemException
import net.akehurst.kotlinx.filesystem.api.ZipErrorReason
import net.akehurst.kotlinx.filesystem.api.ZipException
import net.akehurst.kotlinx.filesystem.api.walkFiles
import kotlin.test.*
import kotlin.time.Instant

class test_ZipFileSystem {

    private suspend fun assertZipError(reason: ZipErrorReason, block: suspend () -> Unit) {
        val e = try {
            block(); null
        } catch (e: ZipException) {
            e
        }
        assertNotNull(e, "expected ZipException $reason")
        assertEquals(reason, e.reason, e.message)
    }

    private suspend fun nested(): DirectoryHandle = ZipFileSystem.open(
        ZipTestBuilder()
            .stored("top.txt", "top level")
            .directory("docs/")
            .stored("docs/readme.txt", "fictional readme")
            .deflated("docs/guides/intro.md", "# Intro\n".repeat(50).encodeToByteArray())
            .stored("docs/guides/deeper/leaf.txt", "leaf")
            .directory("empty/")
            .build(),
        "nested.zip"
    )

    // --- opening ---

    @Test
    fun open_nested_listContent() = runTest {
        val root = nested()
        assertEquals("nested.zip", root.name)
        assertEquals(listOf("top.txt", "docs", "empty"), root.listContent().map { it.name })
        val docs = root.directory("docs")!!
        assertEquals(listOf("readme.txt", "guides"), docs.listContent().map { it.name })
        assertEquals(emptyList(), root.directory("empty")!!.listContent())
    }

    @Test
    fun open_nested_read() = runTest {
        val root = nested()
        assertEquals("top level", root.file("top.txt")!!.readContent())
        assertEquals("# Intro\n".repeat(50), root.directory("docs")!!.directory("guides")!!.file("intro.md")!!.readContent())
        assertEquals("leaf", (root.entry("docs/guides/deeper/leaf.txt") as FileHandle).readContent())
    }

    @Test
    fun open_nested_paths() = runTest {
        val leaf = nested().entry("docs/guides/deeper/leaf.txt") as FileHandle
        assertEquals("/docs/guides/deeper/leaf.txt", leaf.path)
        assertEquals("nested.zip!/docs/guides/deeper/leaf.txt", leaf.absolutePath)
        assertEquals("deeper", leaf.parent!!.name)
    }

    @Test
    fun open_entry_missing() = runTest {
        val root = nested()
        assertNull(root.entry("missing.txt"))
        assertNull(root.file("docs"))
        assertNull(root.directory("top.txt"))
        assertNull(root.entry("top.txt/x"))
    }

    @Test
    fun open_emptyArchive() = runTest {
        val root = ZipFileSystem.open(ZipTestBuilder().build())
        assertEquals(emptyList(), root.listContent())
        assertEquals(emptyList(), root.walkFiles())
    }

    @Test
    fun open_notAZip() = runTest {
        assertZipError(ZipErrorReason.NOT_A_ZIP) { ZipFileSystem.open("this is plain text, not a zip archive at all".encodeToByteArray()) }
    }

    @Test
    fun open_emptyBytes() = runTest {
        assertZipError(ZipErrorReason.NOT_A_ZIP) { ZipFileSystem.open(ByteArray(0)) }
    }

    @Test
    fun open_truncated() = runTest {
        val bytes = ZipTestBuilder().stored("a.txt", "hello").build()
        // keep the end record but drop the start, so the central directory offset points outside
        val truncated = bytes.copyOfRange(20, bytes.size)
        assertZipError(ZipErrorReason.CORRUPT_ARCHIVE) { ZipFileSystem.open(truncated) }
    }

    // --- walk ---

    @Test
    fun walkFiles_relativePaths() = runTest {
        val paths = nested().walkFiles().map { it.relativePath }
        assertEquals(listOf("top.txt", "docs/readme.txt", "docs/guides/intro.md", "docs/guides/deeper/leaf.txt"), paths)
    }

    @Test
    fun walkFiles_fromSubDirectory() = runTest {
        val paths = nested().directory("docs")!!.walkFiles().map { it.relativePath }
        assertEquals(listOf("readme.txt", "guides/intro.md", "guides/deeper/leaf.txt"), paths)
    }

    @Test
    fun walkFiles_globFilter() = runTest {
        val md = nested().walkFiles().filter { it.relativePath.endsWith(".md") }
        assertEquals(listOf("docs/guides/intro.md"), md.map { it.relativePath })
        assertEquals("# Intro\n".repeat(50), md.single().file.readContent())
    }

    // --- metadata ---

    @Test
    fun metadata_stored() = runTest {
        // 2024-03-15 13:45:30, DOS time has 2 second resolution
        val dosDate = ((2024 - 1980) shl 9) or (3 shl 5) or 15
        val dosTime = (13 shl 11) or (45 shl 5) or (30 / 2)
        val content = "fictional content".encodeToByteArray()
        val root = ZipFileSystem.open(ZipTestBuilder().stored("a.txt", content, dosTime = dosTime, dosDate = dosDate).build())
        val md = root.file("a.txt")!!.metadata()
        assertEquals(content.size.toLong(), md.size)
        assertEquals(content.size.toLong(), md.compressedSize)
        assertEquals(ZipArchive.crc32(content), md.crc32)
        assertEquals(Instant.parse("2024-03-15T13:45:30Z"), md.lastModified)
    }

    @Test
    fun metadata_deflated() = runTest {
        val content = ByteArray(10_000) { (it % 7).toByte() }
        val root = ZipFileSystem.open(ZipTestBuilder().deflated("d.bin", content).build())
        val md = root.file("d.bin")!!.metadata()
        assertEquals(10_000L, md.size)
        assertTrue(md.compressedSize!! < 10_000L, "compressed size ${md.compressedSize}")
        assertEquals(ZipArchive.crc32(content), md.crc32)
        assertNull(md.lastModified) // DOS date 0 means unknown
    }

    @Test
    fun metadata_extendedTimestamp() = runTest {
        val seconds = 1_700_000_000L
        val extra = ZipTestBuilder.Out().apply { u16(0x5455); u16(5); u8(1); u32(seconds) }.bytes()
        val root = ZipFileSystem.open(ZipTestBuilder().stored("t.txt", "x".encodeToByteArray(), extra = extra).build())
        assertEquals(Instant.fromEpochSeconds(seconds), root.file("t.txt")!!.metadata().lastModified)
    }

    @Test
    fun crc32_knownValue() {
        // standard check value for CRC-32
        assertEquals(0xCBF43926L, ZipArchive.crc32("123456789".encodeToByteArray()))
    }

    // --- errors when reading entries ---

    @Test
    fun read_encryptedEntry() = runTest {
        val root = ZipFileSystem.open(
            ZipTestBuilder()
                .stored("secret.txt", "not really encrypted".encodeToByteArray(), flags = 0x0001)
                .stored("plain.txt", "plain")
                .build()
        )
        val secret = root.file("secret.txt")!! as FileHandleZip
        assertTrue(secret.isEncrypted)
        assertEquals(20L, secret.metadata().size)
        assertZipError(ZipErrorReason.ENCRYPTED_ENTRY) { secret.readBytes() }
        assertEquals("plain", root.file("plain.txt")!!.readContent())
    }

    @Test
    fun read_unsupportedMethod() = runTest {
        val root = ZipFileSystem.open(ZipTestBuilder().addRaw("b.bz2".encodeToByteArray(), "BZh9...".encodeToByteArray(), method = 12).build())
        assertZipError(ZipErrorReason.UNSUPPORTED_COMPRESSION_METHOD) { root.file("b.bz2")!!.readBytes() }
    }

    @Test
    fun read_badCrc() = runTest {
        val root = ZipFileSystem.open(ZipTestBuilder().addRaw("a.txt".encodeToByteArray(), "hello".encodeToByteArray(), crc = 12345L).build())
        assertZipError(ZipErrorReason.CORRUPT_ARCHIVE) { root.file("a.txt")!!.readBytes() }
    }

    @Test
    fun read_corruptDeflateData() = runTest {
        val garbage = ByteArray(64) { 0xFF.toByte() }
        val root = ZipFileSystem.open(ZipTestBuilder().addRaw("x.bin".encodeToByteArray(), garbage, method = 8, crc = 0, uncompressedSize = 100).build())
        assertZipError(ZipErrorReason.CORRUPT_ARCHIVE) { root.file("x.bin")!!.readBytes() }
    }

    // --- read-only ---

    @Test
    fun readOnly_writes_fail() = runTest {
        val root = nested()
        val file = root.file("top.txt")!!
        assertFailsWith<ReadOnlyFileSystemException> { file.writeContent("changed") }
        assertFailsWith<ReadOnlyFileSystemException> { file.writeBytes(byteArrayOf(1)) }
        assertFailsWith<ReadOnlyFileSystemException> { root.createFile("new.txt") }
        assertFailsWith<ReadOnlyFileSystemException> { root.createDirectory("newDir") }
        assertFailsWith<ReadOnlyFileSystemException> { root.getOrCreateFile("new.txt") }
        assertEquals("top level", file.readContent())
    }

    // --- names ---

    @Test
    fun names_utf8Flag() = runTest {
        val root = ZipFileSystem.open(ZipTestBuilder().stored("Grüße/日本語.txt", "unicode").build())
        assertEquals("unicode", (root.entry("Grüße/日本語.txt") as FileHandle).readContent())
    }

    @Test
    fun names_cp437_withoutUtf8Flag() = runTest {
        // CP437: 0x81 = ü, 0x94 = ö, 0xE1 = ß
        val name = byteArrayOf('M'.code.toByte(), 0x81.toByte(), 0x94.toByte(), 0xE1.toByte(), '.'.code.toByte(), 't'.code.toByte())
        val root = ZipFileSystem.open(ZipTestBuilder().addRaw(name, "cp437".encodeToByteArray(), flags = 0).build())
        assertEquals(listOf("Müöß.t"), root.listContent().map { it.name })
    }

    @Test
    fun names_utf8Bytes_withoutFlag_areReadAsCp437() = runTest {
        // a tool that writes UTF-8 without setting the flag: the name is decoded as CP437, as the zip specification says
        val root = ZipFileSystem.open(ZipTestBuilder().addRaw("é".encodeToByteArray(), "x".encodeToByteArray(), flags = 0).build())
        assertEquals(listOf("├⌐"), root.listContent().map { it.name })
    }

    @Test
    fun names_infoZipUnicodePathExtra() = runTest {
        val cp437Name = "?.txt".encodeToByteArray()
        val utf8 = "ü.txt".encodeToByteArray()
        val extra = ZipTestBuilder.Out().apply { u16(0x7075); u16(5 + utf8.size); u8(1); u32(ZipArchive.crc32(cp437Name)); bytes(utf8) }.bytes()
        val root = ZipFileSystem.open(ZipTestBuilder().addRaw(cp437Name, "x".encodeToByteArray(), flags = 0, extra = extra).build())
        assertEquals(listOf("ü.txt"), root.listContent().map { it.name })
    }

    @Test
    fun names_parentAndAbsolutePaths_stayInsideArchive() = runTest {
        val root = ZipFileSystem.open(
            ZipTestBuilder()
                .stored("../../escape.txt", "1")
                .stored("/absolute/abs.txt", "2")
                .stored("C:\\windows\\win.txt", "3")
                .stored("a/./b/../c.txt", "4")
                .stored("..", "5") // nothing left after removing '..': ignored
                .build()
        )
        val files = root.walkFiles().associate { it.relativePath to it.file.readContent() }
        assertEquals(mapOf("escape.txt" to "1", "absolute/abs.txt" to "2", "windows/win.txt" to "3", "a/b/c.txt" to "4"), files)
        for (f in root.walkFiles()) {
            assertFalse(f.file.path.contains(".."), f.file.path)
            assertTrue(f.file.absolutePath.startsWith("!/"), f.file.absolutePath)
        }
    }

    @Test
    fun names_duplicate_firstWins() = runTest {
        val root = ZipFileSystem.open(ZipTestBuilder().stored("a.txt", "first").stored("./a.txt", "second").build())
        assertEquals(1, root.listContent().size)
        assertEquals("first", root.file("a.txt")!!.readContent())
    }

    // --- ZIP64 ---

    @Test
    fun zip64_records() = runTest {
        val big = ByteArray(5000) { it.toByte() }
        val root = ZipFileSystem.open(
            ZipTestBuilder(zip64 = true)
                .stored("a.txt", "zip64 stored")
                .deflated("dir/b.bin", big)
                .build()
        )
        assertEquals("zip64 stored", root.file("a.txt")!!.readContent())
        val b = root.entry("dir/b.bin") as FileHandle
        assertContentEquals(big, b.readBytes())
        assertEquals(5000L, b.metadata().size)
    }

    // --- zip bomb ---

    @Test
    fun zipBomb_sizeIsKnownBeforeReading() = runTest {
        val zeros = ByteArray(10 * 1024 * 1024)
        val root = ZipFileSystem.open(ZipTestBuilder().deflated("bomb.bin", zeros).build(), options = ZipOptions(maxEntrySize = 1024 * 1024))
        val bomb = root.file("bomb.bin")!!
        val md = bomb.metadata()
        assertEquals(zeros.size.toLong(), md.size)
        assertTrue(md.size!! / md.compressedSize!! > 100, "ratio ${md.size} / ${md.compressedSize}")
        assertZipError(ZipErrorReason.ENTRY_TOO_LARGE) { bomb.readBytes() }
    }

    @Test
    fun zipBomb_lyingSize_stopsInflating() = runTest {
        // the entry says 1000 bytes but inflates to 10 MB: inflating stops after the declared size
        val zeros = ByteArray(10 * 1024 * 1024)
        val root = ZipFileSystem.open(ZipTestBuilder().deflated("liar.bin", zeros, declaredSize = 1000).build())
        assertZipError(ZipErrorReason.CORRUPT_ARCHIVE) { root.file("liar.bin")!!.readBytes() }
    }

    // --- nested archives ---

    @Test
    fun zipInsideZip() = runTest {
        val inner = ZipTestBuilder().stored("inner.txt", "inside").build()
        val root = ZipFileSystem.open(ZipTestBuilder().stored("inner.zip", inner).build(), "outer.zip")
        val innerRoot = root.file("inner.zip")!!.openAsZipDirectory()
        assertEquals("inside", innerRoot.file("inner.txt")!!.readContent())
        assertEquals("outer.zip!/inner.zip!/inner.txt", innerRoot.file("inner.txt")!!.absolutePath)
    }
}
