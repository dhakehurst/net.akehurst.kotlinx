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
/*
package net.akehurst.kotlinx.filesystem

import js.buffer.ArrayBuffer
import js.core.Void
import js.iterable.AsyncIterable
import js.iterable.AsyncIterator
import js.iterable.asFlow
import js.iterable.iterator
import js.typedarrays.Uint8Array
import korlibs.io.file.std.ZipVfs
import korlibs.io.stream.openAsync
import korlibs.js.JSAsyncIterable
import korlibs.js.toFlow
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import net.akehurst.kotlinx.filesystem.DirectoryHandleJS
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.filesystem.api.FileSystem
import net.akehurst.kotlinx.filesystem.api.FileSystemObjectHandle
import web.blob.arrayBuffer
import web.blob.text
import web.experimental.ExperimentalWebApi

import web.fs.FileSystemPermissionMode
import web.fs.createWritable
import web.fs.directory
import web.fs.file
import web.fs.getDirectoryHandle
import web.fs.getFile
import web.fs.getFileHandle
import web.fs.read
import web.fs.readwrite

import web.fs.write
import web.streams.close
import kotlin.js.Promise

external interface Blob {
    //val size:Number
    val type: String

    fun arrayBuffer(): Promise<ArrayBuffer>

    //    fun slice():Blob
//    fun stream():Any
    fun text(): Promise<String>
}

external interface File : Blob {
    // val lastModified:Number
    val name: String
    val webkitRelativePath: String
}

external interface Value {
    val kind: String
    val name: String
}

external interface WritableStream {
    fun close(): Promise<Void>
}

external interface FileSystemWritableFileStream : WritableStream {
    /**
     * data: ArrayBuffer | TypesArray | DataView | Blob | String | { type,data,position,size }
     */
    fun write(data: Any): Promise<Void>
}

external interface FileSystemHandle {
    /**
     * 'file' | 'directory'
     */
    val kind: String
    val name: String
}

external interface FileSystemDirectoryHandle : FileSystemHandle {
    fun values(): AsyncIt<Value>

    fun getDirectoryHandle(name: String): Promise<FileSystemDirectoryHandle>
    fun getFileHandle(name: String): Promise<FileSystemFileHandle>
}

external interface FileSystemFileHandle : FileSystemHandle {
    fun getFile(): Promise<File>
    fun createWritable(): Promise<FileSystemWritableFileStream>
}

data class DirectoryHandleJS(
    val fileSystem: UserFileSystem,
    override val parent: DirectoryHandleJS?,
    val handle: FileSystemDirectoryHandle
) : DirectoryHandleAbstract() {

    override val path: String get() = handle.name

    override val name: String get() = handle.name

    override suspend fun entry(name: String): FileSystemObjectHandle? =
        fileSystem.getEntry(this, name)

    override suspend fun createFile(name: String): FileHandle? {
        TODO("not implemented")
    }

    override suspend fun createDirectory(name: String): DirectoryHandle? {
        TODO("not implemented")
    }

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
                val list = parent.handle.values().toFlow().toList() //FIXME: iterate without making a list
                for (v in list) {
                    when (v.name) {
                        name -> {
                            return when (v.kind) {
                               "file" -> FileHandleJS(this, parent, parent.handle.getFileHandle(v.name).await())
                                "directory" -> DirectoryHandleJS(this, parent, parent.handle.getDirectoryHandle(v.name).await())
                                else -> error("Should not happen")
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
    actual suspend fun selectDirectoryFromDialog(current: DirectoryHandle?, mode: FileAccessMode): DirectoryHandle? {

//        val cur = current?.let { (it as DirectoryHandleJS).handle }
//        val options = when (current) {
//            null -> js("{mode:rw}")
//            else -> js("{mode:rw, startIn:cur}")
//        }

        return try {
            val w: dynamic = window
            val dm = when (mode) {
                FileAccessMode.READ_ONLY -> "read"
                FileAccessMode.READ_WRITE -> "readwrite"
            }
            val p: Promise<dynamic> = w.showDirectoryPicker(js("({mode:dm})"))
            val handle = p.await()
            DirectoryHandleJS(this, current as DirectoryHandleJS?, handle)
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    actual suspend fun selectExistingFileFromDialog(current: DirectoryHandle?, mode: FileAccessMode, useNativeDialog: Boolean): FileHandle? {
        val w: dynamic = window
        val p: Promise<dynamic> = when (mode) {
            FileAccessMode.READ_ONLY -> w.showOpenFilePicker(js("({mode:'read'})"))
            FileAccessMode.READ_WRITE -> w.showOpenFilePicker(js("({mode:'readwrite'})"))
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
                    for (v in dir.handle.values().toFlow().toList()) {  //FIXME: iterate without making a list
                        val o = when (v.kind) {
                            "file" -> FileHandleJS(this, dir, dir.handle.getFileHandle(v.name).await())
                            "directory" -> DirectoryHandleJS(this, dir, dir.handle.getDirectoryHandle(v.name).await())
                            else -> error("Should not happen")
                        }
                        list.add(o)
                    }
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
                    val newFile = parent.handle.getFileHandle(name).await()
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
                    val newDir = parent.handle.getDirectoryHandle(name).await()
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
                    file.handle.getFile().await().text().await()
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
                    val w = file.handle.createWritable().await()
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
            val arrBuf = handle.getFile().await().arrayBuffer().await()
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
*/