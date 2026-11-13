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

import kotlin.time.Instant

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

    /**
     * the content of the file decoded as UTF-8 text.
     * Use [readBytes] for binary files, which decoding would corrupt.
     */
    suspend fun readContent(): String?

    /**
     * the raw bytes of the file, not decoded.
     * return null if the file does not exist or cannot be read
     */
    suspend fun readBytes(): ByteArray?

    /**
     * replace the content of the file.
     * In a browser (JS/Wasm) this asks the user for write permission if the page does not have it yet;
     * the browser only shows that prompt when called soon after a user action such as a click.
     */
    suspend fun writeContent(content: String)

    /**
     * replace the content of the file with these bytes, written as they are.
     * In a browser (JS/Wasm) this asks for write permission as [writeContent] does.
     */
    suspend fun writeBytes(content: ByteArray)

    /**
     * size, modification time and (for zip entries) compressed size and CRC-32 of this file.
     * A field is null when the platform or file system does not provide it.
     * Handles that do not override this return [FileMetadata.UNKNOWN].
     */
    suspend fun metadata(): FileMetadata = FileMetadata.UNKNOWN

    /**
     * open this file as a zip archive; the returned directory is the root of the archive, and is read-only.
     * The whole file is read into memory.
     * @throws ZipException if the file is not a zip archive or the archive cannot be read; [ZipException.reason] says why
     */
    suspend fun openAsZipDirectory(): DirectoryHandle
}

/**
 * metadata of a file; each field is null when it is not known
 */
data class FileMetadata(
    /** uncompressed size in bytes */
    val size: Long?,
    val lastModified: Instant?,
    /** zip entries only: size of the stored (compressed) data in bytes */
    val compressedSize: Long? = null,
    /** zip entries only: CRC-32 of the uncompressed content, as an unsigned 32-bit value */
    val crc32: Long? = null,
) {
    companion object {
        val UNKNOWN = FileMetadata(null, null)
    }
}

enum class ZipErrorReason {
    /** the bytes are not a zip archive (no end of central directory record) */
    NOT_A_ZIP,
    /** the archive structure is damaged or inconsistent, or an entry's content does not match its CRC-32 / size */
    CORRUPT_ARCHIVE,
    /** the entry is encrypted; reading encrypted entries is not supported */
    ENCRYPTED_ENTRY,
    /** the entry uses a compression method other than STORE (0) or DEFLATE (8) */
    UNSUPPORTED_COMPRESSION_METHOD,
    /** the archive uses a feature that is not supported, e.g. it is split over several disks */
    UNSUPPORTED_FEATURE,
    /** the entry is larger than the limit given when the archive was opened */
    ENTRY_TOO_LARGE,
}

class ZipException(
    val reason: ZipErrorReason,
    message: String,
    cause: Throwable? = null
) : Exception("$reason: $message", cause)

/**
 * thrown when writing to or creating entries in a read-only file system, such as an opened zip archive
 */
class ReadOnlyFileSystemException(message: String) : UnsupportedOperationException(message)

/**
 * a file found by [walkFiles], with its path relative to the directory the walk started from
 */
data class RelativeFile(
    /** '/'-separated path relative to the start directory, without a leading '/', e.g. "sub/dir/file.txt" */
    val relativePath: String,
    val file: FileHandle
)

/**
 * all files below this directory, recursively, in the order the directories list them (depth first).
 * Directories themselves are not included. Use [RelativeFile.relativePath] for glob or other filtering.
 */
suspend fun DirectoryHandle.walkFiles(): List<RelativeFile> {
    val result = mutableListOf<RelativeFile>()
    suspend fun walk(dir: DirectoryHandle, prefix: String) {
        for (entry in dir.listContent()) {
            when (entry) {
                is FileHandle -> result.add(RelativeFile(prefix + entry.name, entry))
                is DirectoryHandle -> walk(entry, prefix + entry.name + "/")
            }
        }
    }
    walk(this, "")
    return result
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
     *
     * The dialog used depends on the platform:
     *  - JS/Wasm: the browser directory picker (`showDirectoryPicker`).
     *  - JVM on macOS: the native `java.awt.FileDialog`, with `apple.awt.fileDialogForDirectories=true`.
     *  - JVM on other OSs (Linux, Windows, ...): `javax.swing.JFileChooser` in `DIRECTORIES_ONLY` mode,
     *    because the AWT `FileDialog` there cannot select a directory.
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

    /**
     * the raw bytes of the file; return null if the file does not exist or cannot be read
     */
    suspend fun readFileBytes(file: FileHandle): ByteArray?

    suspend fun writeFileContent(file: FileHandle, content: String)

    suspend fun writeFileBytes(file: FileHandle, content: ByteArray)

    /**
     * open the file as a zip archive; see [FileHandle.openAsZipDirectory]
     * @throws ZipException if the file is not a zip archive or the archive cannot be read
     */
    suspend fun openFileAsZipDirectory(file: FileHandle): DirectoryHandle
}
