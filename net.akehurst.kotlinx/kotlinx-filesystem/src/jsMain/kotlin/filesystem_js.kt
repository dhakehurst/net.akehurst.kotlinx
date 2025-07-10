package net.akehurst.kotlinx.filesystem

//The underlying web.fs lib does not seem to work
// here issues with getFile().text()


/*

import js.iterable.iterator
import js.objects.JsPlainObject
import js.objects.unsafeJso
import kotlinx.browser.window
import kotlinx.coroutines.await
import web.fs.FileSystemDirectoryHandle
import web.fs.FileSystemFileHandle
import web.fs.FileSystemHandleKind
import kotlin.js.Promise
import kotlin.js.iterator

data class DirectoryHandleJS(
    val fileSystem: UserFileSystem,
    val handle: FileSystemDirectoryHandle
) : DirectoryHandleAbstract() {

    override val path: String get() = handle.name

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

@JsPlainObject
external interface FilePickerOptions {
    var mode:String
}

actual object UserFileSystem: FileSystem {
    //actual var useDispatcher: Boolean = false
    actual suspend fun getEntry(parentDirectory: DirectoryHandle, name: String): FileSystemObjectHandle? {
        return when (parentDirectory) {
            is DirectoryHandleJS -> {
                val iter =  parentDirectory.handle.values().iterator()
                while(iter.hasNext()){
                    val v = iter.next()
                    when (v.name) {
                        name -> {
                            return when (v.kind) {
                                FileSystemHandleKind.file -> FileHandleJS(this, parentDirectory.handle.getFileHandle(v.name))
                                FileSystemHandleKind.directory -> DirectoryHandleJS(this, parentDirectory.handle.getDirectoryHandle(v.name))
                                else -> error("Should not happen")
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

    actual suspend fun getDirectory(fullPath:String, mode: FileAccessMode):DirectoryHandle? {
        return selectDirectoryFromDialog(null, mode)
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
        val p: Promise<dynamic> = w.showSaveFilePicker(
            unsafeJso {
//                types = arrayOf(
//                    objectJS {
//                        description = "SysML v2 file"
//                        accept = objectJS {}.set("text/plain", arrayOf(".sysml"))
//                    }
//                )
            }
        )
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
                val iter =  dir.handle.values().iterator()
                while(iter.hasNext()){
                    val v = iter.next()
                    val o = when (v.kind) {
                        FileSystemHandleKind.file -> FileHandleJS(this, dir.handle.getFileHandle(v.name))
                        FileSystemHandleKind.directory -> DirectoryHandleJS(this, dir.handle.getDirectoryHandle(v.name))
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
                file.handle.getFile().text()
            }

            else -> error("FileHandle is not a FileHandleJS: ${file::class.simpleName}")
        }

    actual suspend fun writeFileContent(file: FileHandle, content: String) {
        when (file) {
            is FileHandleJS -> {
                val w = file.handle.createWritable()
                w.write(content)
                w.close()
            }

            else -> error("FileHandle is not a FileHandleJS: ${file::class.simpleName}")
        }
    }

}

 */