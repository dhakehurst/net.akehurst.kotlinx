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

import korlibs.io.file.VfsFile
import korlibs.io.file.baseName
import korlibs.io.file.std.openAsZip
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.filesystem.api.FileSystem
import net.akehurst.kotlinx.filesystem.api.FileSystemObjectHandle

data class DirectoryHandleKorio(
    val fileSystem: FileSystemKorio,
    override val parent: DirectoryHandleKorio?,
    val handle: VfsFile
) : DirectoryHandleAbstract() {

    override val path: String get() = handle.path

    override val name: String get() = handle.pathInfo.baseName

    override suspend fun exists(): Boolean = fileSystem.exists(this)
    override suspend fun entry(name: String): FileSystemObjectHandle? = fileSystem.getEntry(this, name)
    override suspend fun listContent(): List<FileSystemObjectHandle> = fileSystem.listDirectoryContent(this)

    override suspend fun createDirectory(name: String): DirectoryHandle? = fileSystem.createNewDirectory(this, name)
    override suspend fun createFile(name: String): FileHandle? = fileSystem.createNewFile(this, name)

}

data class FileHandleKorio(
    val fileSystem: FileSystemKorio,
    override val parent: DirectoryHandleKorio?,
    val handle: VfsFile
) : FileHandleAbstract() {
    override val name: String get() = handle.pathInfo.baseName
    override val path: String get() = handle.path

    override suspend fun exists(): Boolean = fileSystem.exists(this)
    override suspend fun readContent(): String? = fileSystem.readFileContent(this)
    override suspend fun writeContent(content: String) = fileSystem.writeFileContent(this, content)
    override suspend fun openAsZipDirectory(): DirectoryHandle = fileSystem.openFileAsZipDirectory(this)
}

object FileSystemKorio : FileSystem {

    suspend fun getEntry(parent: DirectoryHandle, name: String): FileSystemObjectHandle? {
        return when (parent) {
            is DirectoryHandleKorio -> {
                val child = parent.handle[name]
                when {
                    child.exists() && child.isDirectory() -> DirectoryHandleKorio(this, parent,child)
                    child.exists() && child.isFile() -> FileHandleKorio(this, parent,child)
                    else -> null //if not found in loop
                }
            }
            else -> null
        }
    }
/*
    suspend fun getDirectory(fullPath: String, mode: FileAccessMode): DirectoryHandle? {
        return selectDirectoryFromDialog(null, mode)
    }

    suspend fun selectDirectoryFromDialog(current: DirectoryHandle?, mode: FileAccessMode): DirectoryHandle? {
        val p = (window as WasmWindow).showDirectoryPicker(
            FilePickerOptions(mode = mode.name)
        )
        return try {
            val handle: FileSystemDirectoryHandle = p.await()
            DirectoryHandleWasmJS(this, handle)
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun selectExistingFileFromDialog(current: DirectoryHandle?, mode: FileAccessMode, useNativeDialog: Boolean): FileHandle? {
        val p = (window as WasmWindow).showOpenFilePicker(
            FilePickerOptions(mode = mode.name)
        )
        return try {
            val handle: FileSystemFileHandle = p.await()
            FileHandleWasmJS(this, handle)
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun selectNewFileFromDialog(parentDirectory: DirectoryHandle): FileHandle? {
        val p = (window as WasmWindow).showSaveFilePicker()
        return try {
            val handle: FileSystemFileHandle = p.await()
            FileHandleWasmJS(this, handle)
        } catch (t: Throwable) {
            null
        }
    }
*/
    suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle> =
        when (dir) {
            is DirectoryHandleKorio -> {
                // skip entries that are neither a directory nor a file (e.g. broken symlinks)
                val list = dir.handle.list().mapNotNull { child ->
                    when {
                        child.isDirectory() -> DirectoryHandleKorio(this, dir,child)
                        child.isFile() -> FileHandleKorio(this, dir,child)
                        else -> null
                    }
                }
                list.toList()
            }

            else -> error("DirectoryHandle is not a DirectoryHandleKorio: ${dir::class.simpleName}")
        }

    suspend fun createNewFile(parent: DirectoryHandle, name: String): FileHandle? {
        return when (parent) {
            is DirectoryHandleKorio -> {
                val newFile = parent.handle[name]
                // only create when missing, so an existing file keeps its content
                if (newFile.exists().not()) newFile.writeString("")
                if (newFile.isFile()) FileHandleKorio(this, parent, newFile) else null
            }

            else -> error("DirectoryHandle is not a DirectoryHandleKorio: ${parent::class.simpleName}")
        }
    }

    suspend fun createNewDirectory(parent: DirectoryHandle, name: String): DirectoryHandle? {
        return when (parent) {
            is DirectoryHandleKorio -> {
                val newDir = parent.handle[name]
                if (newDir.exists().not()) newDir.mkdir()
                if (newDir.isDirectory()) DirectoryHandleKorio(this, parent, newDir) else null
            }

            else -> error("DirectoryHandle is not a DirectoryHandleKorio: ${parent::class.simpleName}")
        }
    }

    suspend fun exists(fileHandle: FileSystemObjectHandle): Boolean {
        return when (fileHandle) {
            is FileHandleKorio -> fileHandle.handle.exists()
            is DirectoryHandleKorio -> fileHandle.handle.exists()
            else -> error("fileHandle is not a DirectoryHandleKorio or a FileHandleKorio: ${fileHandle::class.simpleName}")
        }
    }

    suspend fun readFileContent(file: FileHandle): String? =
        when (file) {
            is FileHandleKorio -> {
                file.handle.readString()
            }

            else -> error("FileHandle is not a FileHandleKorio: ${file::class.simpleName}")
        }

    suspend fun writeFileContent(file: FileHandle, content: String) {
        when (file) {
            is FileHandleKorio -> {
                file.handle.writeString(content)
            }

            else -> error("FileHandle is not a FileHandleKorio: ${file::class.simpleName}")
        }
    }

    suspend fun openFileAsZipDirectory(file: FileHandle): DirectoryHandle {
        val handle = (file as FileHandleKorio).handle
        val zipFs = handle.openAsZip()
        return DirectoryHandleKorio(this, null,zipFs)
    }

}