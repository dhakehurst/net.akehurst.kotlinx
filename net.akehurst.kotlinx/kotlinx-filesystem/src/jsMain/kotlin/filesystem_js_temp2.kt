package net.akehurst.kotlinx.filesystem


import js.buffer.ArrayBuffer
import js.core.Void
import js.iterable.AsyncIterable
import kotlinx.browser.window
import kotlinx.coroutines.await

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
    val handle: FileSystemFileHandle
) : FileHandleAbstract() {

    override val name: String get() = handle.name

    override suspend fun readContent(): String? =
        fileSystem.readFileContent(this)

    override suspend fun writeContent(content: String) =
        fileSystem.writeFileContent(this, content)
}

actual object UserFileSystem : FileSystem {

    actual suspend fun getEntry(parentDirectory: DirectoryHandle, name: String): FileSystemObjectHandle? {
        return when (parentDirectory) {
            is DirectoryHandleJS -> {
                for (v in parentDirectory.handle.values()) {
                    when (v.name) {
                        name -> {
                            return when (v.kind) {
                                "file" -> FileHandleJS(this, parentDirectory.handle.getFileHandle(v.name).await())
                                "directory" -> DirectoryHandleJS(this, parentDirectory.handle.getDirectoryHandle(v.name).await())
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

    actual suspend fun selectDirectoryFromDialog(current: DirectoryHandle?, mode: FileAccessMode): DirectoryHandle? {
        val w: dynamic = window
        val rw = when (mode) {
            FileAccessMode.READ_ONLY -> w.showDirectoryPicker(js("{mode:'read'}"))
            FileAccessMode.READ_WRITE -> w.showDirectoryPicker(js("{mode:'readwrite'}"))
        }
        val cur = current?.let { (it as DirectoryHandleJS).handle }
        val options = when(current) {
            null -> js("{mode:rw}")
            else -> js("{mode:rw, startIn:cur}")
        }
        val p: Promise<dynamic> =w.showDirectoryPicker(options)
        return try {
            val handle: FileSystemDirectoryHandle = p.await()
            DirectoryHandleJS(this, handle)
        } catch (t: Throwable) {
            null
        }
    }

    actual suspend fun selectExistingFileFromDialog(mode: FileAccessMode): FileHandle? {
        val w: dynamic = window
        val p: Promise<dynamic> = when (mode) {
            FileAccessMode.READ_ONLY -> w.showOpenFilePicker(js("{mode:'read'}"))
            FileAccessMode.READ_WRITE -> w.showOpenFilePicker(js("{mode:'readwrite'}"))
        }
        return try {
            val handle: FileSystemFileHandle = p.await()
            FileHandleJS(this, handle)
        } catch (t: Throwable) {
            null
        }
    }

    actual suspend fun selectNewFileFromDialog(parentDirectory: DirectoryHandle): FileHandle? {
        val w: dynamic = window
        val p: Promise<dynamic> = w.showSaveFilePicker(js("{}"))
        return try {
            val handle: FileSystemFileHandle = p.await()
            FileHandleJS(this, handle)
        } catch (t: Throwable) {
            null
        }
    }

    actual suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle> =
        when (dir) {
            is DirectoryHandleJS -> {
                val list = mutableListOf<FileSystemObjectHandle>()
                for (v in dir.handle.values()) {
                    val o = when (v.kind) {
                        "file" -> FileHandleJS(this, dir.handle.getFileHandle(v.name).await())
                        "directory" -> DirectoryHandleJS(this, dir.handle.getDirectoryHandle(v.name).await())
                        else -> error("Should not happen")
                    }
                    list.add(o)
                }
                list
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${dir::class.simpleName}")
        }

    actual suspend fun createNewFile(parentPath: DirectoryHandle): FileHandle? {
        return when (parentPath) {
            is DirectoryHandleJS -> {
                TODO()
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parentPath::class.simpleName}")
        }
    }

    actual suspend fun createNewDirectory(parentPath: DirectoryHandle): DirectoryHandle? {
        return when (parentPath) {
            is DirectoryHandleJS -> {
                TODO()
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parentPath::class.simpleName}")
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

}