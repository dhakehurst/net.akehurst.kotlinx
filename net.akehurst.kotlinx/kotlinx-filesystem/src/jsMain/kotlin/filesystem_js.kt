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

import js.array.toList
import js.iterable.asFlow
import js.objects.unsafeJso
import js.typedarrays.Uint8Array
import korlibs.io.file.std.ZipVfs
import korlibs.io.stream.openAsync
import kotlinx.coroutines.flow.toList
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.filesystem.api.FileAccessMode
import net.akehurst.kotlinx.filesystem.api.UserFileSystem
import net.akehurst.kotlinx.filesystem.api.FileSystemObjectHandle
import web.blob.arrayBuffer
import web.blob.text
import web.experimental.ExperimentalWebApi
import web.fs.*
import web.streams.close

data class DirectoryHandleJS(
    val fileSystem: UserFileSystemDefault,
    override val parent: DirectoryHandleJS?,
    val handle: FileSystemDirectoryHandle
) : DirectoryHandleAbstract() {

    override val name: String get() = handle.name

    override suspend fun exists(): Boolean = fileSystem.exists(this)

    override suspend fun entry(name: String): FileSystemObjectHandle? =
        fileSystem.getEntry(this, name)

    override suspend fun createFile(name: String): FileHandle? =
        fileSystem.createNewFile(this, name)

    override suspend fun createDirectory(name: String): DirectoryHandle? =
        fileSystem.createNewDirectory(this, name)

    override suspend fun listContent(): List<FileSystemObjectHandle> =
        fileSystem.listDirectoryContent(this)

}

data class FileHandleJS(
    val fileSystem: UserFileSystemDefault,
    override val parent: DirectoryHandleJS?,
    val handle: FileSystemFileHandle
) : FileHandleAbstract() {
    override val name: String get() = handle.name

    override suspend fun exists(): Boolean = fileSystem.exists(this)
    override suspend fun readContent(): String? = fileSystem.readFileContent(this)
    override suspend fun writeContent(content: String) = fileSystem.writeFileContent(this, content)
    override suspend fun openAsZipDirectory(): DirectoryHandle? = fileSystem.openFileAsZipDirectory(this)
}

actual object UserFileSystemDefault : UserFileSystem {

    override suspend fun getEntry(parent: DirectoryHandle, name: String): FileSystemObjectHandle? {
        return when {
            name.isEmpty() -> parent
            parent is DirectoryHandleJS -> {
                val list = parent.handle.values().asFlow().toList() //FIXME: iterate without making a list
                for (v in list) {
                    when (v.name) {
                        name -> {
                            return when (v.kind) {
                                FileSystemHandleKind.file -> FileHandleJS(this, parent, parent.handle.getFileHandle(v.name))
                                FileSystemHandleKind.directory -> DirectoryHandleJS(this, parent, parent.handle.getDirectoryHandle(v.name))
                               // else -> error("Should not happen")
                            }
                        }

                        else -> null
                    }
                }
                null
            }

            else -> null
        }
    }

    override suspend fun getDirectory(fullPath: String, mode: FileAccessMode): DirectoryHandle? {
        return selectDirectoryFromDialog("Choose Directory",null, mode)
    }

    @OptIn(ExperimentalWebApi::class)
    override suspend fun selectDirectoryFromDialog(dialogTitle:String,current: DirectoryHandle?, accessMode: FileAccessMode): DirectoryHandle? {
        return try {
            val dpo: DirectoryPickerOptions = when (accessMode) {
                FileAccessMode.READ_ONLY -> unsafeJso { mode = FileSystemPermissionMode.read }
                FileAccessMode.READ_WRITE -> unsafeJso { mode = FileSystemPermissionMode.readwrite }
            }
            val handle: FileSystemDirectoryHandle = showDirectoryPicker(dpo)
            DirectoryHandleJS(fileSystem = this, parent =  null, handle = handle)
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    // the open file picker has no access mode; write permission is requested by createWritable when writing
    @OptIn(ExperimentalWebApi::class)
    override suspend fun selectExistingFileFromDialog(dialogTitle:String,current: DirectoryHandle?, accessMode: FileAccessMode, useNativeDialog: Boolean): FileHandle? {
        return try {
            val cur = current as? DirectoryHandleJS
            val picked = when (cur) {
                null -> showOpenFilePicker()
                else -> showOpenFilePicker(unsafeJso<OpenFilePickerOptions> { startIn = cur.handle })
            }
            picked.toList().firstOrNull()?.let { handle ->
                FileHandleJS(this, parentWithin(cur, handle), handle)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    @OptIn(ExperimentalWebApi::class)
    override suspend fun selectNewFileFromDialog(dialogTitle:String,parent: DirectoryHandle): FileHandle? {
        return try {
            val cur = parent as? DirectoryHandleJS
            val handle: FileSystemFileHandle = when (cur) {
                null -> showSaveFilePicker()
                else -> showSaveFilePicker(unsafeJso<SaveFilePickerOptions> { startIn = cur.handle })
            }
            FileHandleJS(this, parentWithin(cur, handle), handle)
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    /**
     * The user can pick a file in any directory, so [dir] is only used as the parent when the file is inside it.
     * Returns the directory directly containing [file] (building handles for any directories between),
     * or null if [dir] is null or the file is not inside it.
     */
    private suspend fun parentWithin(dir: DirectoryHandleJS?, file: FileSystemFileHandle): DirectoryHandleJS? {
        if (dir == null) return null
        // names from dir down to the file; the last name is the file itself
        val names = dir.handle.resolve(file)?.toList()?.map { it.toString() } ?: return null
        var parent: DirectoryHandleJS = dir
        for (name in names.dropLast(1)) {
            parent = DirectoryHandleJS(this, parent, parent.handle.getDirectoryHandle(name))
        }
        return parent
    }

    override suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle> =
        try {
            when (dir) {
                is DirectoryHandleJS -> {
                    val list = mutableListOf<FileSystemObjectHandle>()
                    dir.handle.values().asFlow().collect { v ->
                        val o = when (v.kind) {
                            FileSystemHandleKind.file -> FileHandleJS(this, dir, dir.handle.getFileHandle(v.name))
                            FileSystemHandleKind.directory -> DirectoryHandleJS(this, dir, dir.handle.getDirectoryHandle(v.name))
                            //   else -> error("Should not happen")
                        }
                        list.add(o)
                    }
//                    for (v in values) {
//                        val o = when (v.kind) {
//                            FileSystemHandleKind.file -> FileHandleJS(this, dir, dir.handle.getFileHandle(v.name))
//                            FileSystemHandleKind.directory -> DirectoryHandleJS(this, dir, dir.handle.getDirectoryHandle(v.name))
//                         //   else -> error("Should not happen")
//                        }
//                        list.add(o)
//                    }
                    list
                }

                else -> error("DirectoryHandle is not a DirectoryHandleJS: ${dir::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            emptyList()
        }

    override suspend fun createNewFile(parent: DirectoryHandle, name: String): FileHandle? {
        return try {
            when (parent) {
                is DirectoryHandleJS -> {
                    // create = true makes an empty file if missing and returns an existing file unchanged;
                    // throws TypeMismatchError (caught below) if a directory has that name
                    val newFile = parent.handle.getFileHandle(name, unsafeJso<FileSystemGetFileOptions> { create = true })
                    FileHandleJS(this, parent, newFile)
                }

                else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parent::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    override suspend fun createNewDirectory(parent: DirectoryHandle, name: String): DirectoryHandle? {
        return try {
            when (parent) {
                is DirectoryHandleJS -> {
                    // create = true makes the directory if missing and returns an existing directory unchanged;
                    // throws TypeMismatchError (caught below) if a file has that name
                    val newDir = parent.handle.getDirectoryHandle(name, unsafeJso<FileSystemGetDirectoryOptions> { create = true })
                    DirectoryHandleJS(this, parent, newDir)
                }

                else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parent::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    override suspend fun exists(entry: FileSystemObjectHandle): Boolean  {
        return when (entry) {
            is FileHandleJS -> try { entry.handle.getFile(); true } catch (_: Exception) { false }
            is DirectoryHandleJS -> try { entry.handle.values(); true } catch (_: Exception) { false }
            else -> error("entry is not a FileHandleJS or a DirectoryHandleJS: ${entry::class.simpleName}")
        }
    }

    override suspend fun readFileContent(file: FileHandle): String? =
        try {
            when (file) {
                is FileHandleJS -> {
                    file.handle.getFile().text()
                }

                else -> error("FileHandle is not a FileHandleJS: ${file::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }

    override suspend fun writeFileContent(file: FileHandle, content: String) {
        try {
            when (file) {
                is FileHandleJS -> {
                    val w = file.handle.createWritable()
                    w.write(content)
                    w.close()
                }

                else -> error("FileHandle is not a FileHandleJS: ${file::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    override suspend fun openFileAsZipDirectory(file: FileHandle): DirectoryHandle? {
        return try {
            val handle = (file as FileHandleJS).handle
            val arrBuf = handle.getFile().arrayBuffer()
            val bytes = Uint8Array(arrBuf)
            val byteArray = ByteArray(bytes.byteLength) { bytes[it].toByte() }
            val zipFs = ZipVfs(byteArray.openAsync())
            FileSystemFromVfs(zipFs).root
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }
}
