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
    /**
     * the files and directories directly inside this directory.
     * Entries that are neither a file nor a directory (e.g. broken symlinks, FIFOs) are left out.
     */
    suspend fun listContent(): List<FileSystemObjectHandle>

    /**
     * return null if the entry does not exist, or is neither a file nor a directory
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

    /**
     * create an empty file with this name.
     * If a file with this name already exists it is returned unchanged (its content is kept).
     * return null if a directory has this name, or if the file cannot be created
     */
    suspend fun createFile(name: String): FileHandle?

    /**
     * create a directory with this name.
     * If a directory with this name already exists it is returned unchanged.
     * return null if a file has this name, or if the directory cannot be created
     */
    suspend fun createDirectory(name: String): DirectoryHandle?

    /**
     * return the existing file, or create it if no entry with that name exists.
     * return null if the entry exists but is not a file, or if it cannot be created
     */
    suspend fun getOrCreateFile(name: String): FileHandle? = when (val entry = entry(name)) {
        null -> createFile(name)
        is FileHandle -> entry
        else -> null
    }

    /**
     * return the existing directory, or create it if no entry with that name exists.
     * return null if the entry exists but is not a directory, or if it cannot be created
     */
    suspend fun getOrCreateDirectory(name: String): DirectoryHandle? = when (val entry = entry(name)) {
        null -> createDirectory(name)
        is DirectoryHandle -> entry
        else -> null
    }
}

interface FileHandle : FileSystemObjectHandle {
    /**
     * the part of the name after the last '.', or "" if there is no '.'
     */
    val extension: String

    /**
     * the name up to (not including) the last '.', or the whole name if there is no '.'
     * (same as kotlin.io File.nameWithoutExtension)
     */
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')

    suspend fun readContent(): String?

    /**
     * replace the content of the file.
     * In a browser (JS/Wasm) this asks the user for write permission if the page does not have it yet;
     * the browser only shows that prompt when called soon after a user action such as a click.
     */
    suspend fun writeContent(content: String)

    /**
     * open this file as a zip archive; the returned directory is the root of the archive
     */
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

    /**
     * ask the user to choose a directory; return null if they cancel.
     * [accessMode] is passed to the browser directory picker on JS/Wasm; other platforms do not use it.
     */
    suspend fun selectDirectoryFromDialog(dialogTitle: String, current: DirectoryHandle? = null, accessMode: FileAccessMode = FileAccessMode.READ_WRITE): DirectoryHandle?

    /**
     * ask the user to choose an existing file; return null if they cancel.
     * The dialog starts in [current] where the platform supports it, but the user may choose a file anywhere.
     * The returned file's parent is the directory that contains it when that is known, otherwise null.
     * [accessMode] is not used: browsers have no access mode for file pickers,
     * and ask for write permission when the file is first written (see [FileHandle.writeContent]).
     */
    suspend fun selectExistingFileFromDialog(dialogTitle: String, current: DirectoryHandle? = null, accessMode: FileAccessMode = FileAccessMode.READ_WRITE, useNativeDialog: Boolean = false): FileHandle?

    /**
     * ask the user to choose where to save a new file; return null if they cancel.
     * The dialog starts in [parent] where the platform supports it, but the user may choose a location anywhere.
     * The returned file's parent is the directory that contains it when that is known, otherwise null.
     */
    suspend fun selectNewFileFromDialog(dialogTitle: String, parent: DirectoryHandle): FileHandle?

    suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle>

    suspend fun createNewFile(parent: DirectoryHandle, name: String): FileHandle?
    suspend fun createNewDirectory(parent: DirectoryHandle, name: String): DirectoryHandle?

    suspend fun exists(entry: FileSystemObjectHandle): Boolean

    suspend fun readFileContent(file: FileHandle): String?

    suspend fun writeFileContent(file: FileHandle, content: String)

    suspend fun openFileAsZipDirectory(file: FileHandle): DirectoryHandle?
}
