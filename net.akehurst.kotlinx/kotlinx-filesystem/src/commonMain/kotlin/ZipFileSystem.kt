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

import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.filesystem.api.FileMetadata
import net.akehurst.kotlinx.filesystem.api.FileSystem
import net.akehurst.kotlinx.filesystem.api.FileSystemObjectHandle
import net.akehurst.kotlinx.filesystem.api.ReadOnlyFileSystemException
import net.akehurst.kotlinx.filesystem.api.ZipErrorReason
import net.akehurst.kotlinx.filesystem.api.ZipException
import kotlin.time.Instant

/**
 * limits applied when reading a zip archive
 */
data class ZipOptions(
    /**
     * an entry whose uncompressed size is larger than this is not read: [FileHandle.readBytes] throws
     * [ZipException] with [ZipErrorReason.ENTRY_TOO_LARGE].
     * Inflating also stops at the entry's declared size, so an entry cannot produce more bytes than its metadata says.
     * The default is the largest ByteArray a platform can hold.
     */
    val maxEntrySize: Long = MAX_BYTE_ARRAY_SIZE,
) {
    companion object {
        const val MAX_BYTE_ARRAY_SIZE: Long = Int.MAX_VALUE - 8L
    }
}

/**
 * Read-only access to zip archives held in memory.
 *
 * Supported: STORE and DEFLATE entries, ZIP64 archives, UTF-8 entry names (flag bit 11 or the Info-ZIP
 * Unicode Path extra field) and CP437 names otherwise, CRC-32 checking when an entry is read.
 * Not supported: encrypted entries, other compression methods, archives split over several disks.
 *
 * Entry names are reduced to plain path segments: '\' is treated as '/', and empty, "." and ".." segments and a
 * leading drive letter ("C:") are dropped, so no entry can refer to anything outside the archive.
 * If two entries end up with the same path, the first one in the central directory is kept.
 *
 * Memory: the whole archive is held in a ByteArray (at most about 2 GB on every platform),
 * and an entry is inflated into a ByteArray of its full size when it is read.
 */
object ZipFileSystem {

    /**
     * parse the archive and return its root directory.
     * @param name the name of the archive, used as the name of the root directory
     * @throws ZipException with [ZipErrorReason.NOT_A_ZIP], [ZipErrorReason.CORRUPT_ARCHIVE] or [ZipErrorReason.UNSUPPORTED_FEATURE]
     */
    fun open(bytes: ByteArray, name: String = "", options: ZipOptions = ZipOptions()): DirectoryHandle {
        val archive = ZipArchive(bytes, name, options)
        return archive.root
    }
}

// ---------------------------------------------------------------------------------------------------------------------

internal class ZipArchive(
    val bytes: ByteArray,
    val archiveName: String,
    val options: ZipOptions
) : FileSystem {

    internal class Entry(
        val path: List<String>,
        val isDirectory: Boolean,
        val flags: Int,
        val method: Int,
        val crc32: Long,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val localHeaderOffset: Long,
        val lastModified: Instant?,
    ) {
        val isEncrypted: Boolean get() = (flags and 0x0001) != 0
    }

    internal class Node(val name: String, val entry: Entry?) {
        val children = LinkedHashMap<String, Node>()
        val isDirectory get() = entry == null || entry.isDirectory
    }

    private val rootNode = Node(archiveName, null)

    val root: DirectoryHandleZip = DirectoryHandleZip(this, null, rootNode)

    init {
        read()
    }

    // --- parsing ---

    private fun fail(reason: ZipErrorReason, message: String): Nothing = throw ZipException(reason, message)

    private fun u16(pos: Long): Int {
        check(pos, 2)
        val p = pos.toInt()
        return (bytes[p].toInt() and 0xFF) or ((bytes[p + 1].toInt() and 0xFF) shl 8)
    }

    private fun u32(pos: Long): Long {
        check(pos, 4)
        val p = pos.toInt()
        return ((bytes[p].toLong() and 0xFF) or
                ((bytes[p + 1].toLong() and 0xFF) shl 8) or
                ((bytes[p + 2].toLong() and 0xFF) shl 16) or
                ((bytes[p + 3].toLong() and 0xFF) shl 24))
    }

    private fun u64(pos: Long): Long {
        val lo = u32(pos)
        val hi = u32(pos + 4)
        if (hi and 0x80000000L != 0L) fail(ZipErrorReason.CORRUPT_ARCHIVE, "64-bit value at $pos is too large")
        return lo or (hi shl 32)
    }

    private fun check(pos: Long, len: Long) {
        if (pos < 0 || len < 0 || pos + len > bytes.size) {
            fail(ZipErrorReason.CORRUPT_ARCHIVE, "data at offset $pos (length $len) is outside the archive (size ${bytes.size})")
        }
    }

    private fun slice(pos: Long, len: Long): ByteArray {
        check(pos, len)
        return bytes.copyOfRange(pos.toInt(), (pos + len).toInt())
    }

    private fun findEndOfCentralDirectory(): Long {
        // EOCD is 22 bytes plus a comment of up to 65535 bytes, at the end of the archive
        if (bytes.size < 22) fail(ZipErrorReason.NOT_A_ZIP, "too small to be a zip archive (${bytes.size} bytes)")
        val lowest = maxOf(0, bytes.size - 22 - 0xFFFF)
        var pos = bytes.size - 22
        while (pos >= lowest) {
            if (bytes[pos] == 0x50.toByte() && bytes[pos + 1] == 0x4B.toByte() && bytes[pos + 2] == 0x05.toByte() && bytes[pos + 3] == 0x06.toByte()) {
                // the comment length must reach exactly to the end, otherwise this is a false match inside other data
                if (pos + 22 + u16(pos + 20L) == bytes.size) return pos.toLong()
            }
            pos--
        }
        fail(ZipErrorReason.NOT_A_ZIP, "no end of central directory record found")
    }

    private fun read() {
        val eocd = findEndOfCentralDirectory()
        val diskNumber = u16(eocd + 4)
        val cdDisk = u16(eocd + 6)
        var entriesOnDisk = u16(eocd + 8).toLong()
        var entryCount = u16(eocd + 10).toLong()
        var cdSize = u32(eocd + 12)
        var cdOffset = u32(eocd + 16)

        // ZIP64: a locator just before the EOCD points to the ZIP64 end of central directory record
        val locator = eocd - 20
        if (locator >= 0 && u32(locator) == 0x07064B50L) {
            val zip64Eocd = u64(locator + 8)
            if (u32(zip64Eocd) != 0x06064B50L) fail(ZipErrorReason.CORRUPT_ARCHIVE, "ZIP64 end of central directory record not found at $zip64Eocd")
            val disk64 = u32(zip64Eocd + 16)
            val cdDisk64 = u32(zip64Eocd + 20)
            if (disk64 != 0L || cdDisk64 != 0L) fail(ZipErrorReason.UNSUPPORTED_FEATURE, "archives split over several disks are not supported")
            entriesOnDisk = u64(zip64Eocd + 24)
            entryCount = u64(zip64Eocd + 32)
            cdSize = u64(zip64Eocd + 40)
            cdOffset = u64(zip64Eocd + 48)
        } else if (diskNumber != 0 || cdDisk != 0) {
            fail(ZipErrorReason.UNSUPPORTED_FEATURE, "archives split over several disks are not supported")
        }
        if (entriesOnDisk != entryCount) fail(ZipErrorReason.UNSUPPORTED_FEATURE, "archives split over several disks are not supported")
        check(cdOffset, cdSize)

        var pos = cdOffset
        val cdEnd = cdOffset + cdSize
        for (i in 0 until entryCount) {
            if (pos + 46 > cdEnd) fail(ZipErrorReason.CORRUPT_ARCHIVE, "central directory is shorter than its $entryCount entries")
            if (u32(pos) != 0x02014B50L) fail(ZipErrorReason.CORRUPT_ARCHIVE, "bad central directory entry signature at $pos")
            val flags = u16(pos + 8)
            val method = u16(pos + 10)
            val dosTime = u16(pos + 12)
            val dosDate = u16(pos + 14)
            val crc = u32(pos + 16)
            var compressedSize = u32(pos + 20)
            var uncompressedSize = u32(pos + 24)
            val nameLen = u16(pos + 28)
            val extraLen = u16(pos + 30)
            val commentLen = u16(pos + 32)
            val externalAttributes = u32(pos + 38)
            var localHeaderOffset = u32(pos + 42)
            val nameBytes = slice(pos + 46, nameLen.toLong())
            val extraStart = pos + 46 + nameLen
            check(extraStart, extraLen.toLong())

            var unicodeName: String? = null
            var extendedTime: Instant? = null
            // extra fields: 2 byte id, 2 byte size, data
            var x = extraStart
            val extraEnd = extraStart + extraLen
            while (x + 4 <= extraEnd) {
                val id = u16(x)
                val size = u16(x + 2)
                val data = x + 4
                if (data + size > extraEnd) break // malformed extra field: ignore the rest
                when (id) {
                    0x0001 -> { // ZIP64 extended information: only the fields that are 0xFFFFFFFF in the header, in this order
                        var p = data
                        if (uncompressedSize == 0xFFFFFFFFL && p + 8 <= data + size) { uncompressedSize = u64(p); p += 8 }
                        if (compressedSize == 0xFFFFFFFFL && p + 8 <= data + size) { compressedSize = u64(p); p += 8 }
                        if (localHeaderOffset == 0xFFFFFFFFL && p + 8 <= data + size) { localHeaderOffset = u64(p); p += 8 }
                    }

                    0x7075 -> { // Info-ZIP Unicode Path: version, CRC-32 of the header name, UTF-8 name
                        if (size >= 5 && bytes[data.toInt()].toInt() == 1 && u32(data + 1) == crc32(nameBytes)) {
                            unicodeName = slice(data + 5, size - 5L).decodeToString()
                        }
                    }

                    0x5455 -> { // extended timestamp: flags, then modification time in seconds since 1970 UTC
                        if (size >= 5 && (bytes[data.toInt()].toInt() and 0x01) != 0) {
                            extendedTime = Instant.fromEpochSeconds(u32(data + 1).toInt().toLong())
                        }
                    }
                }
                x = data + size
            }

            val rawName = unicodeName ?: if ((flags and 0x0800) != 0) nameBytes.decodeToString() else decodeCp437(nameBytes)
            // MS-DOS directory attribute (0x10) marks a directory too, for archives that do not end the name with '/'
            val isDirectory = rawName.endsWith('/') || rawName.endsWith('\\') || (externalAttributes and 0x10L) != 0L && uncompressedSize == 0L
            val path = safePath(rawName)
            if (path.isNotEmpty()) {
                val entry = Entry(
                    path = path,
                    isDirectory = isDirectory,
                    flags = flags,
                    method = method,
                    crc32 = crc,
                    compressedSize = compressedSize,
                    uncompressedSize = uncompressedSize,
                    localHeaderOffset = localHeaderOffset,
                    lastModified = extendedTime ?: dosDateTime(dosDate, dosTime),
                )
                add(entry)
            }
            pos = extraStart + extraLen + commentLen
        }
    }

    private fun add(entry: Entry) {
        var node = rootNode
        for ((i, segment) in entry.path.withIndex()) {
            val last = i == entry.path.lastIndex
            val existing = node.children[segment]
            node = when {
                existing == null -> Node(segment, if (last) entry else null).also { node.children[segment] = it }
                // the first entry with a path is kept; an explicit directory entry may give metadata to an implicit directory
                last && existing.entry == null && entry.isDirectory && existing.isDirectory -> Node(segment, entry).also {
                    it.children.putAll(existing.children)
                    node.children[segment] = it
                }

                last -> return
                existing.isDirectory -> existing
                else -> return // a file is in the way: the entry cannot be placed
            }
        }
    }

    // --- reading entries ---

    suspend fun read(entry: Entry): ByteArray {
        val name = entry.path.joinToString("/")
        if (entry.isEncrypted) throw ZipException(ZipErrorReason.ENCRYPTED_ENTRY, "entry '$name' is encrypted")
        if (entry.uncompressedSize > options.maxEntrySize) {
            throw ZipException(ZipErrorReason.ENTRY_TOO_LARGE, "entry '$name' is ${entry.uncompressedSize} bytes, larger than the limit of ${options.maxEntrySize}")
        }
        if (entry.method != 0 && entry.method != 8) {
            throw ZipException(ZipErrorReason.UNSUPPORTED_COMPRESSION_METHOD, "entry '$name' uses compression method ${entry.method}${methodName(entry.method)}; only STORE (0) and DEFLATE (8) are supported")
        }
        val lh = entry.localHeaderOffset
        if (u32(lh) != 0x04034B50L) fail(ZipErrorReason.CORRUPT_ARCHIVE, "bad local header signature for entry '$name' at $lh")
        val dataStart = lh + 30 + u16(lh + 26) + u16(lh + 28)
        val compressed = slice(dataStart, entry.compressedSize)
        val size = entry.uncompressedSize.toInt()
        val content = when (entry.method) {
            0 -> {
                if (entry.compressedSize != entry.uncompressedSize) fail(ZipErrorReason.CORRUPT_ARCHIVE, "stored entry '$name' has different compressed and uncompressed sizes")
                compressed
            }

            else -> try {
                inflateRaw(compressed, size)
            } catch (e: ZipException) {
                throw e
            } catch (t: Throwable) {
                throw ZipException(ZipErrorReason.CORRUPT_ARCHIVE, "entry '$name' could not be inflated: ${t.message}", t)
            }
        }
        if (content.size != size) fail(ZipErrorReason.CORRUPT_ARCHIVE, "entry '$name' inflated to ${content.size} bytes but its size is $size")
        if (crc32(content) != entry.crc32) fail(ZipErrorReason.CORRUPT_ARCHIVE, "entry '$name' does not match its CRC-32")
        return content
    }

    private fun methodName(method: Int) = when (method) {
        9 -> " (DEFLATE64)"
        12 -> " (BZIP2)"
        14 -> " (LZMA)"
        93 -> " (ZSTD)"
        95 -> " (XZ)"
        99 -> " (AES encryption)"
        else -> ""
    }

    companion object {
        /**
         * split a name from the archive into plain segments, so it cannot point outside the archive
         */
        internal fun safePath(rawName: String): List<String> {
            var name = rawName.replace('\\', '/')
            // drive letter, e.g. "C:/x" or "C:x"
            if (name.length >= 2 && name[1] == ':' && name[0].isLetter()) name = name.substring(2)
            return name.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }
        }

        /**
         * DOS date and time have no time zone; they are read as UTC
         */
        internal fun dosDateTime(dosDate: Int, dosTime: Int): Instant? {
            if (dosDate == 0) return null
            val year = 1980 + ((dosDate shr 9) and 0x7F)
            val month = (dosDate shr 5) and 0x0F
            val day = dosDate and 0x1F
            val hour = (dosTime shr 11) and 0x1F
            val minute = (dosTime shr 5) and 0x3F
            val second = (dosTime and 0x1F) * 2
            if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 59) return null
            val days = daysFromCivil(year, month, day)
            return Instant.fromEpochSeconds(days * 86400L + hour * 3600L + minute * 60L + second)
        }

        // days since 1970-01-01 (proleptic Gregorian), from Howard Hinnant's algorithm
        private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
            val y = if (month <= 2) year - 1 else year
            val era = (if (y >= 0) y else y - 399) / 400
            val yoe = y - era * 400
            val mp = (month + 9) % 12
            val doy = (153 * mp + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146097L + doe - 719468L
        }

        private val CRC_TABLE = LongArray(256) { n ->
            var c = n.toLong()
            repeat(8) { c = if (c and 1L != 0L) 0xEDB88320L xor (c ushr 1) else c ushr 1 }
            c
        }

        fun crc32(data: ByteArray): Long {
            var c = 0xFFFFFFFFL
            for (b in data) c = CRC_TABLE[((c xor b.toLong()) and 0xFF).toInt()] xor (c ushr 8)
            return c xor 0xFFFFFFFFL
        }

        // code page 437 characters for bytes 0x80..0xFF; bytes below 0x80 are ASCII
        private const val CP437_HIGH =
            "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u00A0"

        internal fun decodeCp437(bytes: ByteArray): String = buildString(bytes.size) {
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                append(if (v < 0x80) v.toChar() else CP437_HIGH[v - 0x80])
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------

class DirectoryHandleZip internal constructor(
    private val archive: ZipArchive,
    override val parent: DirectoryHandleZip?,
    private val node: ZipArchive.Node
) : DirectoryHandleAbstract() {

    override val name: String get() = node.name

    // the root has an empty path, so entries are "/a", "/a/b.txt"
    override val path: String get() = if (parent == null) "" else "${parent.path}/$name"
    override val absolutePath: String get() = if (parent == null) "${archive.archiveName}!/" else "${archive.archiveName}!$path"

    /** the modification time of the directory entry, if the archive has one for this directory */
    val lastModified: Instant? get() = node.entry?.lastModified

    override suspend fun exists(): Boolean = true

    override suspend fun listContent(): List<FileSystemObjectHandle> = node.children.values.map { handleFor(it) }

    override suspend fun entry(name: String): FileSystemObjectHandle? {
        if (name.isEmpty()) return this
        // allow a relative path such as "sub/file.txt"
        var current: ZipArchive.Node = node
        var handle: DirectoryHandleZip = this
        val segments = name.split('/').filter { it.isNotEmpty() }
        for ((i, s) in segments.withIndex()) {
            val child = current.children[s] ?: return null
            if (i == segments.lastIndex) return handle.handleFor(child)
            if (!child.isDirectory) return null
            handle = DirectoryHandleZip(archive, handle, child)
            current = child
        }
        return this
    }

    private fun handleFor(child: ZipArchive.Node): FileSystemObjectHandle =
        if (child.isDirectory) DirectoryHandleZip(archive, this, child) else FileHandleZip(archive, this, child.entry!!)

    override suspend fun createFile(name: String): FileHandle? = throw ReadOnlyFileSystemException("cannot create file '$name' in '$absolutePath': zip archives are read-only")
    override suspend fun createDirectory(name: String): DirectoryHandle? = throw ReadOnlyFileSystemException("cannot create directory '$name' in '$absolutePath': zip archives are read-only")

    override fun toString(): String = "DirectoryHandleZip($absolutePath)"
}

class FileHandleZip internal constructor(
    private val archive: ZipArchive,
    override val parent: DirectoryHandleZip,
    private val entry: ZipArchive.Entry
) : FileHandleAbstract() {

    override val name: String get() = entry.path.last()
    override val path: String get() = "${parent.path}/$name"
    override val absolutePath: String get() = "${archive.archiveName}!$path"

    /** true if the entry is encrypted; reading it throws [ZipException] with [ZipErrorReason.ENCRYPTED_ENTRY] */
    val isEncrypted: Boolean get() = entry.isEncrypted

    /** the zip compression method number, e.g. 0 = STORE, 8 = DEFLATE */
    val compressionMethod: Int get() = entry.method

    override suspend fun exists(): Boolean = true

    override suspend fun metadata(): FileMetadata = FileMetadata(
        size = entry.uncompressedSize,
        lastModified = entry.lastModified,
        compressedSize = entry.compressedSize,
        crc32 = entry.crc32
    )

    /**
     * @throws ZipException if the entry cannot be read, e.g. it is encrypted, uses an unsupported method, is too large or is corrupt
     */
    override suspend fun readBytes(): ByteArray = archive.read(entry)

    /**
     * @throws ZipException if the entry cannot be read; see [readBytes]
     */
    override suspend fun readContent(): String = readBytes().decodeToString()

    override suspend fun writeContent(content: String) = throw ReadOnlyFileSystemException("cannot write '$absolutePath': zip archives are read-only")
    override suspend fun writeBytes(content: ByteArray) = throw ReadOnlyFileSystemException("cannot write '$absolutePath': zip archives are read-only")

    override suspend fun openAsZipDirectory(): DirectoryHandle = ZipFileSystem.open(readBytes(), absolutePath, archive.options)

    override fun toString(): String = "FileHandleZip($absolutePath)"
}
