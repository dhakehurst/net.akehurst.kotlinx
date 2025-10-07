package net.akehurst.kotlinx.filesystem

import js.buffer.ArrayBuffer
import js.core.Void
import js.iterable.AsyncIterable
import js.iterable.asFlow
import js.iterable.iterator
import js.typedarrays.Uint8Array
import korlibs.io.file.std.ZipVfs
import korlibs.io.stream.openAsync
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import net.akehurst.kotlinx.filesystem.DirectoryHandleJS
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.filesystem.api.FileSystem
import net.akehurst.kotlinx.filesystem.api.FileSystemObjectHandle
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
    fun values(): AsyncIterable<Value>

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
    override suspend fun openAsZipDirectory(): DirectoryHandle = fileSystem.openFileAsZipDirectory(this)
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

    actual suspend fun selectDirectoryFromDialog(current: DirectoryHandle?, mode: FileAccessMode): DirectoryHandle? {
        val w: dynamic = window
        val rw = when (mode) {
            FileAccessMode.READ_ONLY -> w.showDirectoryPicker(js("{mode:'read'}"))
            FileAccessMode.READ_WRITE -> w.showDirectoryPicker(js("{mode:'readwrite'}"))
        }
        val cur = current?.let { (it as DirectoryHandleJS).handle }
        val options = when (current) {
            null -> js("{mode:rw}")
            else -> js("{mode:rw, startIn:cur}")
        }
        val p: Promise<dynamic> = w.showDirectoryPicker(options)
        return try {
            val handle: FileSystemDirectoryHandle = p.await()
            DirectoryHandleJS(this, current as DirectoryHandleJS?, handle)
        } catch (t: Throwable) {
            null
        }
    }

    actual suspend fun selectExistingFileFromDialog(current: DirectoryHandle?, mode: FileAccessMode, useNativeDialog: Boolean): FileHandle? {
        val w: dynamic = window
        val p: Promise<dynamic> = when (mode) {
            FileAccessMode.READ_ONLY -> w.showOpenFilePicker(js("{mode:'read'}"))
            FileAccessMode.READ_WRITE -> w.showOpenFilePicker(js("{mode:'readwrite'}"))
        }
        return try {
            val handle: FileSystemFileHandle = p.await()
            FileHandleJS(this, current as DirectoryHandleJS?, handle)
        } catch (t: Throwable) {
            null
        }
    }

    actual suspend fun selectNewFileFromDialog(parent: DirectoryHandle): FileHandle? {
        val w: dynamic = window
        val p: Promise<dynamic> = w.showSaveFilePicker(js("{}"))
        return try {
            val handle: FileSystemFileHandle = p.await()
            FileHandleJS(this, parent as DirectoryHandleJS, handle)
        } catch (t: Throwable) {
            null
        }
    }

    actual suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle> =
        when (dir) {
            is DirectoryHandleJS -> {
                val list = mutableListOf<FileSystemObjectHandle>()
                for (v in dir.handle.values().asFlow().toList()) {  //FIXME: iterate without making a list
                    val o = when (v.kind) {
                        "file" -> FileHandleJS(this, dir,dir.handle.getFileHandle(v.name).await())
                        "directory" -> DirectoryHandleJS(this, dir,dir.handle.getDirectoryHandle(v.name).await())
                        else -> error("Should not happen")
                    }
                    list.add(o)
                }
                list
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${dir::class.simpleName}")
        }

    actual suspend fun createNewFile(parent: DirectoryHandle, name: String): FileHandle? {
        return when (parent) {
            is DirectoryHandleJS -> {
                val newFile = parent.handle.getFileHandle(name).await()
                newFile.createWritable().await()
                FileHandleJS(this, parent,newFile)
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parent::class.simpleName}")
        }
    }

    actual suspend fun createNewDirectory(parent: DirectoryHandle, name: String): DirectoryHandle? {
        return when (parent) {
            is DirectoryHandleJS -> {
                val newDir = parent.handle.getDirectoryHandle(name).await()
                DirectoryHandleJS(this, parent,newDir)
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parent::class.simpleName}")
        }
    }

    actual suspend fun readFileContent(file: FileHandle): String? =
        when (file) {
            is FileHandleJS -> {
                file.handle.getFile().await().text().await()
            }

            else -> error("FileHandle is not a FileHandleJS: ${file::class.simpleName}")
        }

    actual suspend fun writeFileContent(file: FileHandle, content: String) {
        when (file) {
            is FileHandleJS -> {
                val w = file.handle.createWritable().await()
                w.write(content).await()
                w.close().await()
            }

            else -> error("FileHandle is not a FileHandleJS: ${file::class.simpleName}")
        }
    }

    actual suspend fun openFileAsZipDirectory(file: FileHandle): DirectoryHandle {
        val handle = (file as FileHandleJS).handle
        val arrBuf = handle.getFile().await().arrayBuffer().await()
        val bytes = Uint8Array(arrBuf)
        val byteArray = ByteArray(bytes.byteLength) { bytes[it].toByte() }
        val zipFs = ZipVfs(byteArray.openAsync())
        return DirectoryHandleKorio(FileSystemKorio, null,zipFs)
    }
}
