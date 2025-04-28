package net.akehurst.kotlinx.filesystem

//The underlying web.fs lib does not seem to work
// here does not compile due to ReadableStream

/*
import js.objects.JsPlainObject
import kotlinx.coroutines.await
import web.fs.FileSystemDirectoryHandle
import web.fs.FileSystemFileHandle
import web.fs.FileSystemHandleKind
import web.window.window
import kotlin.js.Promise

data class DirectoryHandleJS(
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

external interface WasmWindow {
    fun showDirectoryPicker(options: FilePickerOptions? = definedExternally): Promise<FileSystemDirectoryHandle>
    fun showSaveFilePicker(options: FilePickerOptions? = definedExternally): Promise<FileSystemFileHandle>
    fun showOpenFilePicker(options: FilePickerOptions? = definedExternally): Promise<JsArray<FileSystemFileHandle>>
    fun alert(message: String)
    fun prompt(message: String, defaultText: String): String?
}

@JsPlainObject
external class FilePickerOptions(
    var mode:String
)

//fun Window.showSaveFilePicker(options: FilePickerOptions): Promise<FileSystemFileHandle> {
 //   js("this.showSaveFilePicker(options)")
//}
////fun Window.showOpenFilePicker(options: FilePickerOptions): Promise<FileSystemFileHandle> {
//    js("this.showOpenFilePicker(options)")
//}

actual object UserFileSystem {
    actual var useDispatcher: Boolean = false

    actual suspend fun getEntry(parentDirectory: DirectoryHandle, name: String): FileSystemObjectHandle? {
        return when (parentDirectory) {
            is DirectoryHandleJS -> {
                for (v in parentDirectory.handle.values()) {
                    when (v.name) {
                        name -> {
                            return when (v.kind) {
                                FileSystemHandleKind.file -> FileHandleJS(this, parentDirectory.handle.getFileHandle(v.name))
                                FileSystemHandleKind.directory -> DirectoryHandleJS(this, parentDirectory.handle.getDirectoryHandle(v.name))
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

    actual suspend fun selectDirectoryFromDialog(current: DirectoryHandle?): DirectoryHandle? {
        val p = (window as WasmWindow).showDirectoryPicker(
            FilePickerOptions(mode = "readwrite")
        )
        return try {
            val handle: FileSystemDirectoryHandle = p.await()
            DirectoryHandleJS(this, handle)
        } catch (t: Throwable) {
            null
        }
    }

    actual suspend fun selectExistingFileFromDialog(): FileHandle? {
        val p = (window as WasmWindow).showOpenFilePicker(
           FilePickerOptions(mode = "readwrite")
        )
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
                        FileSystemHandleKind.file -> FileHandleJS(this, dir.handle.getFileHandle(v.name))
                        FileSystemHandleKind.directory -> DirectoryHandleJS(this, dir.handle.getDirectoryHandle(v.name))
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
                file.handle.getFile().text()
            }

            else -> error("FileHandle is not a FileHandleJS: ${file::class.simpleName}")
        }

    actual suspend fun writeFileContent(file: FileHandle, content: String) {
        when (file) {
            is FileHandleJS -> {
                val w = file.handle.createWritable()
                w.write(content.toJsString())
                w.close()
            }

            else -> error("FileHandle is not a FileHandleJS: ${file::class.simpleName}")
        }
    }

}
*/