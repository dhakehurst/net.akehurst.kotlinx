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
 * inflate raw DEFLATE data (no zlib or gzip header), as stored in zip entries.
 * Stops with an exception if the data would inflate to more than [expectedSize] bytes,
 * so a lying size field cannot make it use more memory than that.
 * The result may be shorter than [expectedSize] if the data is truncated; the caller checks the size.
 */
internal expect suspend fun inflateRaw(data: ByteArray, expectedSize: Int): ByteArray
