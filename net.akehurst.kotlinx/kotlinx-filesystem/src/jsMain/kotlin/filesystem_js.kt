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

import js.iterable.asFlow
import js.objects.unsafeJso
import js.promise.await
import js.typedarrays.Uint8Array
import korlibs.io.file.std.ZipVfs
import korlibs.io.stream.openAsync
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.toList
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.filesystem.api.FileSystem
import net.akehurst.kotlinx.filesystem.api.FileSystemObjectHandle
import web.blob.arrayBuffer
import web.blob.text
import web.experimental.ExperimentalWebApi
import web.fs.*
import web.streams.close
import kotlin.js.Promise

data class DirectoryHandleJS(
    val fileSystem: UserFileSystem,
    override val parent: DirectoryHandleJS?,
    val handle: FileSystemDirectoryHandle
) : DirectoryHandleAbstract() {

    override val name: String get() = handle.name

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
    val fileSystem: UserFileSystem,
    override val parent: DirectoryHandleJS?,
    val handle: FileSystemFileHandle
) : FileHandleAbstract() {
    override val name: String get() = handle.name
    override suspend fun readContent(): String? = fileSystem.readFileContent(this)
    override suspend fun writeContent(content: String) = fileSystem.writeFileContent(this, content)
    override suspend fun openAsZipDirectory(): DirectoryHandle? = fileSystem.openFileAsZipDirectory(this)
}

actual object UserFileSystem : FileSystem {

    actual suspend fun getEntry(parent: DirectoryHandle, name: String): FileSystemObjectHandle? {
        return when (parent) {
            is DirectoryHandleJS -> {
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

    actual suspend fun getDirectory(fullPath: String, mode: FileAccessMode): DirectoryHandle? {
        return selectDirectoryFromDialog(null, mode)
    }

    @OptIn(ExperimentalWebApi::class)
    actual suspend fun selectDirectoryFromDialog(current: DirectoryHandle?, accessMode: FileAccessMode): DirectoryHandle? {
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

    actual suspend fun selectExistingFileFromDialog(current: DirectoryHandle?, accessMode: FileAccessMode, useNativeDialog: Boolean): FileHandle? {
        val w: dynamic = window
        val p: Promise<dynamic> = when (accessMode) {
            FileAccessMode.READ_ONLY -> w.showOpenFilePicker(js("{mode:'read'}"))
            FileAccessMode.READ_WRITE -> w.showOpenFilePicker(js("{mode:'readwrite'}"))
        }
        return try {
            val handle: FileSystemFileHandle = p.await()
            FileHandleJS(this, current as DirectoryHandleJS?, handle)
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    actual suspend fun selectNewFileFromDialog(parent: DirectoryHandle): FileHandle? {
        return try {
            val w: dynamic = window
            val p: Promise<dynamic> = w.showSaveFilePicker(js("{}"))
            val handle: FileSystemFileHandle = p.await()
            FileHandleJS(this, parent as DirectoryHandleJS, handle)
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    actual suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle> =
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

    actual suspend fun createNewFile(parent: DirectoryHandle, name: String): FileHandle? {
        return try {
            when (parent) {
                is DirectoryHandleJS -> {
                    val newFile = parent.handle.getFileHandle(name)
                    newFile.createWritable()
                    FileHandleJS(this, parent, newFile)
                }

                else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parent::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    actual suspend fun createNewDirectory(parent: DirectoryHandle, name: String): DirectoryHandle? {
        return try {
            when (parent) {
                is DirectoryHandleJS -> {
                    val newDir = parent.handle.getDirectoryHandle(name)
                    DirectoryHandleJS(this, parent, newDir)
                }

                else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parent::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    actual suspend fun readFileContent(file: FileHandle): String? =
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

    actual suspend fun writeFileContent(file: FileHandle, content: String) {
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

    actual suspend fun openFileAsZipDirectory(file: FileHandle): DirectoryHandle? {
        return try {
            val handle = (file as FileHandleJS).handle
            val arrBuf = handle.getFile().arrayBuffer()
            val bytes = Uint8Array(arrBuf)
            val byteArray = ByteArray(bytes.byteLength) { bytes[it].toByte() }
            val zipFs = ZipVfs(byteArray.openAsync())
            DirectoryHandleKorio(FileSystemKorio, null, zipFs)
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }
}
