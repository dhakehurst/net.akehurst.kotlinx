package net.akehurst.kotlinx.filesystem

import js.buffer.ArrayBuffer
import js.core.Void
import js.iterable.AsyncIterable
import js.objects.JsPlainObject
import kotlinx.coroutines.await
import web.window.window
import kotlin.js.Promise

external interface Blob : JsAny {
    //val size:Number
    val type:String

    fun arrayBuffer():Promise<ArrayBuffer>
    //    fun slice():Blob
//    fun stream():Any
    fun text():Promise<JsString>
}
external interface File : Blob {
   // val lastModified:Number
    val name : String
    val webkitRelativePath:String
}
external interface Value : JsAny {
    val kind:String
    val name:String
}
external interface WritableStream: JsAny {
    fun close():Promise<Void>
}
external interface FileSystemWritableFileStream : WritableStream {
    /**
     * data: ArrayBuffer | TypesArray | DataView | Blob | String | { type,data,position,size }
     */
    fun write(data:JsAny):Promise<Void>
}
external interface FileSystemHandle: JsAny {
    /**
     * 'file' | 'directory'
     */
    val kind:String
    val name:String
}
external interface FileSystemDirectoryHandle : FileSystemHandle {
    fun values(): AsyncIterable<Value>

    fun getDirectoryHandle(name:String):FileSystemDirectoryHandle
    fun getFileHandle(name:String):Promise<FileSystemFileHandle>
}
external interface FileSystemFileHandle : FileSystemHandle {
    fun getFile(): Promise<File>
    fun createWritable():Promise<FileSystemWritableFileStream>
}


data class DirectoryHandleJS(
    val fileSystem: UserFileSystem,
    val handle: FileSystemDirectoryHandle
) : DirectoryHandleAbstract() {

    override val path: String get() = TODO()

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
) : FileHandle {
    override val name: String get() = handle.name
    override val extension: String get() = name.substringAfterLast('.')

    override suspend fun readContent(): String? =
        fileSystem.readFileContent(this)

    override suspend fun writeContent(content: String) =
        fileSystem.writeFileContent(this, content)
}

@JsPlainObject
external class FilePickerOptions(
    var mode:String
)

external interface WasmWindow {
    fun showDirectoryPicker(options: FilePickerOptions? = definedExternally): Promise<FileSystemDirectoryHandle>
    fun showSaveFilePicker(options: FilePickerOptions? = definedExternally): Promise<FileSystemFileHandle>
    fun showOpenFilePicker(options: FilePickerOptions? = definedExternally): Promise<JsArray<FileSystemFileHandle>>
    fun alert(message: String)
    fun prompt(message: String, defaultText: String): String?
}

actual object UserFileSystem {

    actual suspend fun getEntry(parentDirectory: DirectoryHandle, name: String): FileSystemObjectHandle? {
        return when (parentDirectory) {
            is DirectoryHandleJS -> {
                for (v in parentDirectory.handle.values()) {
                    when (v.name) {
                        name -> {
                            return when (v.kind) {
                                "file" -> FileHandleJS(this, parentDirectory.handle.getFileHandle(v.name).await())
                                "directory" -> DirectoryHandleJS(this, parentDirectory.handle.getDirectoryHandle(v.name))
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

    actual suspend fun selectDirectoryFromDialog(current: DirectoryHandle?,mode: FileAccessMode): DirectoryHandle? {
        val opts = when(mode) {
            FileAccessMode.READ_ONLY -> FilePickerOptions("read")
            FileAccessMode.READ_WRITE -> FilePickerOptions("readwrite")
        }
        val p = (window as WasmWindow).showDirectoryPicker(opts)
        return try {
            val handle: FileSystemDirectoryHandle = p.await()
            DirectoryHandleJS(this, handle)
        } catch (t: Throwable) {
            null
        }
    }

    actual suspend fun selectExistingFileFromDialog(mode: FileAccessMode): FileHandle? {
        val opts = when(mode) {
            FileAccessMode.READ_ONLY -> FilePickerOptions("read")
            FileAccessMode.READ_WRITE -> FilePickerOptions("readwrite")
        }
        val p = (window as WasmWindow).showOpenFilePicker(opts)
        return try {
            val handle: FileSystemFileHandle = p.await()
            FileHandleJS(this, handle)
        } catch (t: Throwable) {
            null
        }
    }

    actual suspend fun selectNewFileFromDialog(parentDirectory: DirectoryHandle): FileHandle? {
        val p = (window as WasmWindow).showSaveFilePicker()
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
                        "directory" -> DirectoryHandleJS(this, dir.handle.getDirectoryHandle(v.name))
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
                file.handle.getFile().await<File>().text().await<JsString>().toString()
            }

            else -> error("FileHandle is not a FileHandleJS: ${file::class.simpleName}")
        }

    actual suspend fun writeFileContent(file: FileHandle, content: String) {
        when (file) {
            is FileHandleJS -> {
                val w = file.handle.createWritable().await<FileSystemWritableFileStream>()
                w.write(content.toJsString()).await<Void>()
                w.close().await()
            }

            else -> error("FileHandle is not a FileHandleJS: ${file::class.simpleName}")
        }
    }

}