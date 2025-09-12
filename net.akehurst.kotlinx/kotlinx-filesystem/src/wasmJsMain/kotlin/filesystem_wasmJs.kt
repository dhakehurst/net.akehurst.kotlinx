package net.akehurst.kotlinx.filesystem

//The underlying web.fs lib does not seem to work
// here does not compile due to ReadableStream


import js.core.JsPrimitives.toByte
import js.iterable.iterator
import js.objects.JsPlainObject
import korlibs.io.file.std.ZipVfs
import korlibs.io.stream.openAsync
import kotlinx.coroutines.await
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.filesystem.api.FileSystem
import net.akehurst.kotlinx.filesystem.api.FileSystemObjectHandle
import web.blob.bytes

import web.blob.text
import web.fs.*
import web.streams.close
import web.window.window
import kotlin.js.Promise

data class DirectoryHandleWasmJS(
    val fileSystem: UserFileSystem,
    val handle: FileSystemDirectoryHandle
) : DirectoryHandleAbstract() {

    override val path: String get() = TODO()

    override val name: String get() = handle.name

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
    val handle: FileSystemFileHandle
) : FileHandle {
    override val name: String get() = handle.name
    override val extension: String get() = name.substringAfterLast('.')

    override suspend fun readContent(): String? = fileSystem.readFileContent(this)
    override suspend fun writeContent(content: String) = fileSystem.writeFileContent(this, content)
    override suspend fun openAsZipDirectory() : DirectoryHandle = fileSystem.openFileAsZipDirectory(this)
}

external interface WasmWindow {
    fun showDirectoryPicker(options: FilePickerOptions? = definedExternally): Promise<FileSystemDirectoryHandle>
    fun showSaveFilePicker(options: FilePickerOptions? = definedExternally): Promise<FileSystemFileHandle>
    fun showOpenFilePicker(options: FilePickerOptions? = definedExternally): Promise<JsArray<FileSystemFileHandle>>
    fun alert(message: String)
    fun prompt(message: String, defaultText: String): String?
}

@JsPlainObject
external class FilePickerOptions(
    var mode: String
)

//fun Window.showSaveFilePicker(options: FilePickerOptions): Promise<FileSystemFileHandle> {
//   js("this.showSaveFilePicker(options)")
//}
////fun Window.showOpenFilePicker(options: FilePickerOptions): Promise<FileSystemFileHandle> {
//    js("this.showOpenFilePicker(options)")
//}

actual object UserFileSystem : FileSystem {

    actual suspend fun getEntry(parentDirectory: DirectoryHandle, name: String): FileSystemObjectHandle? {
        return when (parentDirectory) {
            is DirectoryHandleWasmJS -> {
                val iter = parentDirectory.handle.values().iterator()
                while (iter.hasNext()) {
                    val v = iter.next()
                    when (v.name) {
                        name -> {
                            return when (v.kind) {
                                FileSystemHandleKind.file -> FileHandleWasmJS(this, parentDirectory.handle.getFileHandle(v.name))
                                FileSystemHandleKind.directory -> DirectoryHandleWasmJS(this, parentDirectory.handle.getDirectoryHandle(v.name))
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
        return selectDirectoryFromDialog(null, mode)
    }

    actual suspend fun selectDirectoryFromDialog(current: DirectoryHandle?, mode: FileAccessMode): DirectoryHandle? {
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

    actual suspend fun selectExistingFileFromDialog(current: DirectoryHandle?, mode: FileAccessMode, useNativeDialog:Boolean): FileHandle? {
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

    actual suspend fun selectNewFileFromDialog(parentDirectory: DirectoryHandle): FileHandle? {
        val p = (window as WasmWindow).showSaveFilePicker()
        return try {
            val handle: FileSystemFileHandle = p.await()
            FileHandleWasmJS(this, handle)
        } catch (t: Throwable) {
            null
        }
    }

    actual suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle> =
        when (dir) {
            is DirectoryHandleWasmJS -> {
                val list = mutableListOf<FileSystemObjectHandle>()
                val iter = dir.handle.values().iterator()
                while (iter.hasNext()) {
                    val v = iter.next()
                    val o = when (v.kind) {
                        FileSystemHandleKind.file -> FileHandleWasmJS(this, dir.handle.getFileHandle(v.name))
                        FileSystemHandleKind.directory -> DirectoryHandleWasmJS(this, dir.handle.getDirectoryHandle(v.name))
                        else -> error("Should not happen")
                    }
                    list.add(o)
                }
                list
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${dir::class.simpleName}")
        }

    actual suspend fun createNewFile(parentPath: DirectoryHandle, name: String): FileHandle? {
        return when (parentPath) {
            is DirectoryHandleWasmJS -> {
                val newFile = parentPath.handle.getFileHandle(name)
                newFile.createWritable()
                FileHandleWasmJS(this, newFile)
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parentPath::class.simpleName}")
        }
    }

    actual suspend fun createNewDirectory(parentPath: DirectoryHandle, name: String): DirectoryHandle? {
        return when (parentPath) {
            is DirectoryHandleWasmJS -> {
                val newDir = parentPath.handle.getDirectoryHandle(name)
                DirectoryHandleWasmJS(this, newDir)
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parentPath::class.simpleName}")
        }
    }

    actual suspend fun readFileContent(file: FileHandle): String? =
        when (file) {
            is FileHandleWasmJS -> {
                file.handle.getFile().text()
            }

            else -> error("FileHandle is not a FileHandleJS: ${file::class.simpleName}")
        }

    actual suspend fun writeFileContent(file: FileHandle, content: String) {
        when (file) {
            is FileHandleWasmJS -> {
                val w = file.handle.createWritable()
                w.write(content.toJsString())
                w.close()
            }

            else -> error("FileHandle is not a FileHandleJS: ${file::class.simpleName}")
        }
    }

    actual suspend fun openFileAsZipDirectory(file: FileHandle): DirectoryHandle {
        val handle = (file as FileHandleWasmJS).handle
        val bytes = handle.getFile().bytes()
        val byteArray = ByteArray(bytes.byteLength) { bytes[it].toByte() }
        val zipFs = ZipVfs(byteArray.openAsync())
        return DirectoryHandleKorio(FileSystemKorio, zipFs)
    }

}