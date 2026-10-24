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

package net.akehurst.kotlinx.filesystem.api

interface FileSystem {
}

interface FileSystemObjectHandle {
    val parent: DirectoryHandle?
    val name: String
    val path: String
    val absolutePath: String

    suspend fun exists(): Boolean
}

interface DirectoryHandle : FileSystemObjectHandle {
    suspend fun listContent(): List<FileSystemObjectHandle>

    /**
     * return null if the entry does not exist
     */
    suspend fun entry(name: String): FileSystemObjectHandle?

    /**
     * return null if the file does not exist
     */
    suspend fun file(name: String): FileHandle?

    /**
     * return null if the directory does not exist
     */
    suspend fun directory(name: String): DirectoryHandle?

    suspend fun createFile(name: String): FileHandle?
    suspend fun createDirectory(name: String): DirectoryHandle?
}

interface FileHandle : FileSystemObjectHandle {
    val extension: String
    suspend fun readContent(): String?
    suspend fun writeContent(content: String)
    suspend fun openAsZipDirectory(): DirectoryHandle?
}

enum class FileAccessMode {
    READ_ONLY,
    READ_WRITE
}

interface UserFileSystem : FileSystem {

    suspend fun getEntry(parent: DirectoryHandle, name: String): FileSystemObjectHandle?

    /**
     * for JS/Wasm, this just calls the selectDirectoryFromDialog method
     */
    suspend fun getDirectory(fullPath: String, mode: FileAccessMode = FileAccessMode.READ_WRITE): DirectoryHandle?

    suspend fun selectDirectoryFromDialog(dialogTitle: String, current: DirectoryHandle? = null, accessMode: FileAccessMode = FileAccessMode.READ_WRITE): DirectoryHandle?
    suspend fun selectExistingFileFromDialog(dialogTitle: String, current: DirectoryHandle? = null, accessMode: FileAccessMode = FileAccessMode.READ_WRITE, useNativeDialog: Boolean = false): FileHandle?
    suspend fun selectNewFileFromDialog(dialogTitle: String, parent: DirectoryHandle): FileHandle?

    suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle>

    suspend fun createNewFile(parent: DirectoryHandle, name: String): FileHandle?
    suspend fun createNewDirectory(parent: DirectoryHandle, name: String): DirectoryHandle?

    suspend fun exists(entry: FileSystemObjectHandle): Boolean

    suspend fun readFileContent(file: FileHandle): String?

    suspend fun writeFileContent(file: FileHandle, content: String)

    suspend fun openFileAsZipDirectory(file: FileHandle): DirectoryHandle?
}
