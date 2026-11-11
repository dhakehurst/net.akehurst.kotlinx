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
import web.compression.DecompressionStream
import web.compression.deflateRaw
import web.streams.ReadableWritablePair
import web.streams.read

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
// uses the browser's built-in DecompressionStream("deflate-raw")
internal actual suspend fun inflateRaw(data: ByteArray, expectedSize: Int): ByteArray {
    val input = Blob(listOf<BlobPart>(data.toUint8Array()).toJsArray())
    val inflater = DecompressionStream(CompressionFormat.deflateRaw)
    val output = input.stream().pipeThrough(inflater.unsafeCast<ReadableWritablePair<Uint8Array<ArrayBuffer>, Uint8Array<ArrayBuffer>>>())
    val reader = output.getReader()
    val out = ByteArray(expectedSize)
    var n = 0
    try {
        while (true) {
            val result = reader.read()
            if (result.done) break
            val chunk = result.value?.toByteArray() ?: continue
            // stop as soon as the data produces more than its declared size
            if (n + chunk.size > expectedSize) {
                reader.cancelAsync()
                error("inflates to more than the declared $expectedSize bytes")
            }
            chunk.copyInto(out, n)
            n += chunk.size
        }
    } finally {
        reader.releaseLock()
    }
    return if (n == expectedSize) out else out.copyOf(n)
}
