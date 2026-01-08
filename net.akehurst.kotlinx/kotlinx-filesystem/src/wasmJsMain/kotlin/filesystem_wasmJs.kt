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

@file:OptIn(ExperimentalWasmJsInterop::class)

package net.akehurst.kotlinx.filesystem

import js.core.JsPrimitives.toKotlinByte
import js.iterable.asFlow
import js.objects.unsafeJso
import korlibs.io.file.std.ZipVfs
import korlibs.io.stream.openAsync
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.toList
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.filesystem.api.FileSystem
import net.akehurst.kotlinx.filesystem.api.FileSystemObjectHandle
import web.blob.bytes
import web.blob.text
import web.experimental.ExperimentalWebApi
import web.fs.*
import web.streams.close
import web.window.window
import kotlin.js.Promise


data class DirectoryHandleWasmJS(
    val fileSystem: UserFileSystem,
    override val parent: DirectoryHandleWasmJS?,
    val handle: FileSystemDirectoryHandle
) : DirectoryHandleAbstract() {

    override val name: String get() = handle.name

    override suspend fun exists(): Boolean = fileSystem.exists(this)

    override suspend fun entry(name: String): FileSystemObjectHandle? =
        fileSystem.getEntry(this, name)

    override suspend fun listContent(): List<FileSystemObjectHandle> =
        fileSystem.listDirectoryContent(this)

    override suspend fun createDirectory(name: String): DirectoryHandle? {
        TODO("not implemented")
    }

    override suspend fun createFile(name: String): FileHandle? {
        TODO("not implemented")
    }

}

data class FileHandleWasmJS(
    val fileSystem: UserFileSystem,
    override val parent: DirectoryHandleWasmJS?,
    val handle: FileSystemFileHandle
) : FileHandleAbstract() {
    override val name: String get() = handle.name

    override suspend fun exists(): Boolean = fileSystem.exists(this)
    override suspend fun readContent(): String? = fileSystem.readFileContent(this)
    override suspend fun writeContent(content: String) = fileSystem.writeFileContent(this, content)
    override suspend fun openAsZipDirectory(): DirectoryHandle? = fileSystem.openFileAsZipDirectory(this)
}

external interface WasmWindow {
    //fun showDirectoryPicker(options: JsAny? = definedExternally): Promise<FileSystemDirectoryHandle>
    fun showSaveFilePicker(options: JsAny? = definedExternally): Promise<FileSystemFileHandle>
    fun showOpenFilePicker(options: JsAny? = definedExternally): Promise<JsArray<FileSystemFileHandle>>
    fun alert(message: String)
    fun prompt(message: String, defaultText: String): String?
}

fun FilePickerOptions(mode: String): JsAny = js("({mode:mode})")

actual object UserFileSystem : FileSystem {

    actual suspend fun getEntry(parent: DirectoryHandle, name: String): FileSystemObjectHandle? {
        return when {
            name.isEmpty() -> parent
            parent is DirectoryHandleWasmJS -> {
                val list = parent.handle.values().asFlow().toList()  //FIXME: iterate without making a list
                for (v in list) {
                    when (v.name) {
                        name -> {
                            return when (v.kind) {
                                FileSystemHandleKind.file -> FileHandleWasmJS(this, parent, parent.handle.getFileHandle(v.name))
                                FileSystemHandleKind.directory -> DirectoryHandleWasmJS(this, parent, parent.handle.getDirectoryHandle(v.name))
                            }
                        }

                        else -> null
                    }
                }
                null //if not found in loop
            }

            else -> null
        }
    }

    actual suspend fun getDirectory(fullPath: String, mode: FileAccessMode): DirectoryHandle? {
        return selectDirectoryFromDialog("Choose Directory",null, mode)
    }

    @OptIn(ExperimentalWebApi::class)
    actual suspend fun selectDirectoryFromDialog(dialogTitle:String,current: DirectoryHandle?, accessMode: FileAccessMode): DirectoryHandle? {
        return try {
            val dpo: DirectoryPickerOptions = when (accessMode) {
                FileAccessMode.READ_ONLY -> unsafeJso { mode = FileSystemPermissionMode.read }
                FileAccessMode.READ_WRITE -> unsafeJso { mode = FileSystemPermissionMode.readwrite }
            }
            val handle: FileSystemDirectoryHandle = showDirectoryPicker(dpo)
            DirectoryHandleWasmJS(this, null, handle)
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    actual suspend fun selectExistingFileFromDialog(dialogTitle:String,current: DirectoryHandle?, accessMode: FileAccessMode, useNativeDialog: Boolean): FileHandle? {
        return try {
            val p = (window as WasmWindow).showOpenFilePicker(
                FilePickerOptions(mode = accessMode.name)
            )
            val handle: FileSystemFileHandle = p.await()
            FileHandleWasmJS(this, null, handle)
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    actual suspend fun selectNewFileFromDialog(dialogTitle:String,parent: DirectoryHandle): FileHandle? {
        val p = (window as WasmWindow).showSaveFilePicker()
        return try {
            val handle: FileSystemFileHandle = p.await()
            FileHandleWasmJS(this, parent as DirectoryHandleWasmJS, handle)
        } catch (t: Throwable) {
            null
        }
    }

    actual suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle> =
        try {
            when (dir) {
                is DirectoryHandleWasmJS -> {
                    val list = mutableListOf<FileSystemObjectHandle>()
                    val values = dir.handle.values().asFlow().toList()  //FIXME: iterate without making a list
                    for (v in values) {
                        val o = when (v.kind) {
                            FileSystemHandleKind.file -> FileHandleWasmJS(this, dir, dir.handle.getFileHandle(v.name))
                            FileSystemHandleKind.directory -> DirectoryHandleWasmJS(this, dir, dir.handle.getDirectoryHandle(v.name))
                          //  else -> error("Should not happen")
                        }
                        list.add(o)
                    }
                    list
                }

                else -> error("DirectoryHandle is not a DirectoryHandleWasmJS: ${dir::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            emptyList()
        }

    actual suspend fun createNewFile(parent: DirectoryHandle, name: String): FileHandle? {
        try {
            return when (parent) {
                is DirectoryHandleWasmJS -> {
                    val newFile = parent.handle.getFileHandle(name)
                    newFile.createWritable()
                    FileHandleWasmJS(this, parent, newFile)
                }

                else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parent::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }

    actual suspend fun createNewDirectory(parent: DirectoryHandle, name: String): DirectoryHandle? {
        try {
            return when (parent) {
                is DirectoryHandleWasmJS -> {
                    val newDir = parent.handle.getDirectoryHandle(name)
                    DirectoryHandleWasmJS(this, parent, newDir)
                }

                else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parent::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }

    actual suspend fun exists(entry: FileSystemObjectHandle): Boolean  {
        return when (entry) {
            is FileHandleWasmJS -> try { entry.handle.getFile(); true } catch (_: Exception) { false }
            is DirectoryHandleWasmJS -> try { entry.handle.values(); true } catch (_: Exception) { false }
            else -> error("entry is not a FileHandleWasmJS or a DirectoryHandleWasmJS: ${entry::class.simpleName}")
        }
    }

    actual suspend fun readFileContent(file: FileHandle): String? =
        try {
            when (file) {
                is FileHandleWasmJS -> {
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
                is FileHandleWasmJS -> {
                    val w = file.handle.createWritable()
                    w.write(content.toJsString())
                    w.close()
                }

                else -> error("FileHandle is not a FileHandleJS: ${file::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    actual suspend fun openFileAsZipDirectory(file: FileHandle): DirectoryHandle? {
        try {
            val handle = (file as FileHandleWasmJS).handle
            val bytes = handle.getFile().bytes()
            val byteArray = ByteArray(bytes.byteLength) { bytes[it].toKotlinByte() }
            val zipFs = ZipVfs(byteArray.openAsync())
            return DirectoryHandleKorio(FileSystemKorio, null, zipFs)
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }

}