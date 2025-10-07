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

import net.akehurst.kotlinx.filesystem.api.*

enum class FileAccessMode {
    READ_ONLY,
    READ_WRITE
}


abstract class DirectoryHandleAbstract: DirectoryHandle {
    override suspend fun directory(name: String): DirectoryHandle? {
        val entry = entry(name)
        return when (entry) {
            is DirectoryHandle -> entry
            else -> null
        }
    }

    override suspend fun file(name: String): FileHandle? {
        val entry = entry(name)
        return when (entry) {
            is FileHandle -> entry
            else -> null
        }
    }
}

abstract class FileHandleAbstract : FileHandle {
    override val extension: String get() = name.substringAfterLast('.')
}