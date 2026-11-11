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

import java.util.zip.Inflater

internal actual suspend fun inflateRaw(data: ByteArray, expectedSize: Int): ByteArray {
    val inflater = Inflater(true) // true = raw DEFLATE, no zlib header
    try {
        inflater.setInput(data)
        // one byte more than expected, so too much output is detected
        val out = ByteArray(expectedSize + 1)
        var n = 0
        while (!inflater.finished() && n < out.size) {
            val r = inflater.inflate(out, n, out.size - n)
            if (r == 0 && (inflater.needsInput() || inflater.needsDictionary())) break // truncated data
            n += r
        }
        if (n > expectedSize) error("inflates to more than the declared $expectedSize bytes")
        return out.copyOf(n)
    } finally {
        inflater.end()
    }
}
