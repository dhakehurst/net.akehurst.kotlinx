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

/**
 * raw DEFLATE (no header) of [data], for building test archives
 */
expect suspend fun deflateForTest(data: ByteArray): ByteArray

/**
 * writes small zip archives byte by byte, so tests can produce cases that normal zip tools do not:
 * ZIP64 records, CP437 names, encrypted flags, wrong CRCs, lying sizes, unsafe names.
 * All content is fictional test data.
 */
class ZipTestBuilder(private val zip64: Boolean = false) {

    class Entry(
        val nameBytes: ByteArray,
        val data: ByteArray,               // stored bytes (already compressed if method is 8)
        val method: Int,
        val crc: Long,
        val uncompressedSize: Long,
        val flags: Int,
        val dosTime: Int,
        val dosDate: Int,
        val extra: ByteArray,
    )

    private val entries = mutableListOf<Entry>()

    fun addRaw(
        nameBytes: ByteArray,
        data: ByteArray,
        method: Int = 0,
        crc: Long = ZipArchive.crc32(data),
        uncompressedSize: Long = data.size.toLong(),
        flags: Int = 0,
        dosTime: Int = 0,
        dosDate: Int = 0,
        extra: ByteArray = ByteArray(0),
    ): ZipTestBuilder {
        entries.add(Entry(nameBytes, data, method, crc, uncompressedSize, flags, dosTime, dosDate, extra))
        return this
    }

    /** a STORE entry with a UTF-8 name (flag bit 11 set) */
    fun stored(name: String, content: ByteArray, flags: Int = 0x0800, dosTime: Int = 0, dosDate: Int = 0, extra: ByteArray = ByteArray(0)) =
        addRaw(name.encodeToByteArray(), content, 0, flags = flags, dosTime = dosTime, dosDate = dosDate, extra = extra)

    fun stored(name: String, content: String) = stored(name, content.encodeToByteArray())

    fun directory(name: String) = addRaw(name.encodeToByteArray(), ByteArray(0), flags = 0x0800)

    /** a DEFLATE entry with a UTF-8 name */
    suspend fun deflated(name: String, content: ByteArray, declaredSize: Long = content.size.toLong()): ZipTestBuilder =
        addRaw(name.encodeToByteArray(), deflateForTest(content), 8, crc = ZipArchive.crc32(content), uncompressedSize = declaredSize, flags = 0x0800)

    fun build(): ByteArray {
        val out = Out()
        val offsets = mutableListOf<Long>()
        for (e in entries) {
            offsets.add(out.size.toLong())
            val zip64Extra = if (zip64) Out().apply { u16(0x0001); u16(16); u64(e.uncompressedSize); u64(e.data.size.toLong()) }.bytes() else ByteArray(0)
            out.u32(0x04034B50L)
            out.u16(if (zip64) 45 else 20)
            out.u16(e.flags)
            out.u16(e.method)
            out.u16(e.dosTime)
            out.u16(e.dosDate)
            out.u32(e.crc)
            out.u32(if (zip64) 0xFFFFFFFFL else e.data.size.toLong())
            out.u32(if (zip64) 0xFFFFFFFFL else e.uncompressedSize)
            out.u16(e.nameBytes.size)
            out.u16(zip64Extra.size + e.extra.size)
            out.bytes(e.nameBytes)
            out.bytes(zip64Extra)
            out.bytes(e.extra)
            out.bytes(e.data)
        }
        val cdOffset = out.size.toLong()
        for ((i, e) in entries.withIndex()) {
            val zip64Extra = if (zip64) Out().apply { u16(0x0001); u16(24); u64(e.uncompressedSize); u64(e.data.size.toLong()); u64(offsets[i]) }.bytes() else ByteArray(0)
            out.u32(0x02014B50L)
            out.u16(if (zip64) 45 else 20) // version made by
            out.u16(if (zip64) 45 else 20) // version needed
            out.u16(e.flags)
            out.u16(e.method)
            out.u16(e.dosTime)
            out.u16(e.dosDate)
            out.u32(e.crc)
            out.u32(if (zip64) 0xFFFFFFFFL else e.data.size.toLong())
            out.u32(if (zip64) 0xFFFFFFFFL else e.uncompressedSize)
            out.u16(e.nameBytes.size)
            out.u16(zip64Extra.size + e.extra.size)
            out.u16(0) // comment
            out.u16(0) // disk
            out.u16(0) // internal attributes
            out.u32(0) // external attributes
            out.u32(if (zip64) 0xFFFFFFFFL else offsets[i])
            out.bytes(e.nameBytes)
            out.bytes(zip64Extra)
            out.bytes(e.extra)
        }
        val cdSize = out.size.toLong() - cdOffset
        if (zip64) {
            val zip64EocdOffset = out.size.toLong()
            out.u32(0x06064B50L)
            out.u64(44) // size of the rest of the record
            out.u16(45); out.u16(45)
            out.u32(0); out.u32(0)
            out.u64(entries.size.toLong()); out.u64(entries.size.toLong())
            out.u64(cdSize); out.u64(cdOffset)
            out.u32(0x07064B50L)
            out.u32(0)
            out.u64(zip64EocdOffset)
            out.u32(1)
        }
        out.u32(0x06054B50L)
        out.u16(0); out.u16(0)
        val count = if (zip64 || entries.size > 0xFFFF) 0xFFFF else entries.size
        out.u16(count); out.u16(count)
        out.u32(if (zip64) 0xFFFFFFFFL else cdSize)
        out.u32(if (zip64) 0xFFFFFFFFL else cdOffset)
        out.u16(0)
        return out.bytes()
    }

    class Out {
        private var buf = ByteArray(1024)
        var size = 0; private set
        private fun ensure(n: Int) {
            if (size + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + n))
        }
        fun u8(v: Int) { ensure(1); buf[size++] = v.toByte() }
        fun u16(v: Int) { u8(v and 0xFF); u8((v shr 8) and 0xFF) }
        fun u32(v: Long) { for (i in 0 until 4) u8(((v shr (8 * i)) and 0xFF).toInt()) }
        fun u64(v: Long) { for (i in 0 until 8) u8(((v shr (8 * i)) and 0xFF).toInt()) }
        fun bytes(b: ByteArray) { ensure(b.size); b.copyInto(buf, size); size += b.size }
        fun bytes(): ByteArray = buf.copyOf(size)
    }
}
