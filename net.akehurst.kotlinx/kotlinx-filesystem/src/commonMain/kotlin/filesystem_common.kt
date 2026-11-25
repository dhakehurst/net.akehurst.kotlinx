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


abstract class DirectoryHandleAbstract: DirectoryHandle {
    // Workaround for a Kotlin/JS bug (fixed in Kotlin 2.5.0, probably KT-90587): when an exported abstract class
    // does not redeclare a suspend function of an exported interface, a subclass's override of it gets no JS
    // suspend bridge, and calls through the interface fail with "is not a function". Remove on Kotlin 2.5.0.
    abstract override suspend fun exists(): Boolean
    abstract override suspend fun listContent(): List<FileSystemObjectHandle>
    abstract override suspend fun entry(name: String): FileSystemObjectHandle?
    abstract override suspend fun createFile(name: String): FileHandle?
    abstract override suspend fun createDirectory(name: String): DirectoryHandle?
    // the interface's default methods are not callable on subclasses on JS either (KT-84710), so delegate to them here
    override suspend fun getOrCreateFile(name: String): FileHandle? = super.getOrCreateFile(name)
    override suspend fun getOrCreateDirectory(name: String): DirectoryHandle? = super.getOrCreateDirectory(name)


    override val path: String get() = "${parent?.path?:""}/$name"
    override val absolutePath: String get() = path

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
    // Workaround for the same Kotlin/JS bug, see DirectoryHandleAbstract. Remove on Kotlin 2.5.0.
    abstract override suspend fun exists(): Boolean
    abstract override suspend fun readContent(): String?
    abstract override suspend fun readBytes(): ByteArray?
    abstract override suspend fun writeContent(content: String)
    abstract override suspend fun writeBytes(content: ByteArray)
    abstract override suspend fun openAsZipDirectory(): DirectoryHandle
    override suspend fun metadata(): FileMetadata = FileMetadata.UNKNOWN

    override val extension: String get() = name.substringAfterLast('.', "")

    override val path: String get() = "${parent?.path?:""}/$name"
    override val absolutePath: String get() = path

}