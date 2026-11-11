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

import js.array.toJsArray
import js.buffer.ArrayBuffer
import js.typedarrays.Uint8Array
import js.typedarrays.toByteArray
import js.typedarrays.toUint8Array
import web.blob.Blob
import web.blob.BlobPart
import web.compression.CompressionFormat
import web.compression.CompressionStream
import web.compression.deflateRaw
import web.streams.ReadableWritablePair
import web.streams.read

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
actual suspend fun deflateForTest(data: ByteArray): ByteArray {
    val input = Blob(listOf<BlobPart>(data.toUint8Array()).toJsArray())
    val deflater = CompressionStream(CompressionFormat.deflateRaw)
    val reader = input.stream().pipeThrough(deflater.unsafeCast<ReadableWritablePair<Uint8Array<ArrayBuffer>, Uint8Array<ArrayBuffer>>>()).getReader()
    val chunks = mutableListOf<ByteArray>()
    while (true) {
        val result = reader.read()
        if (result.done) break
        result.value?.let { chunks.add(it.toByteArray()) }
    }
    val out = ByteArray(chunks.sumOf { it.size })
    var n = 0
    for (c in chunks) { c.copyInto(out, n); n += c.size }
    return out
}
