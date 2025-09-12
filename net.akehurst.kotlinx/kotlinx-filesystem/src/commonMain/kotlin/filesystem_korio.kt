package net.akehurst.kotlinx.filesystem

import korlibs.io.file.VfsFile
import korlibs.io.file.baseName
import korlibs.io.file.std.openAsZip
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.filesystem.api.FileSystem
import net.akehurst.kotlinx.filesystem.api.FileSystemObjectHandle

data class DirectoryHandleKorio(
    val fileSystem: FileSystemKorio,
    val handle: VfsFile
) : DirectoryHandleAbstract() {

    override val path: String get() = TODO()

    override val name: String get() = handle.pathInfo.baseName

    override suspend fun entry(name: String): FileSystemObjectHandle? = fileSystem.getEntry(this, name)
    override suspend fun listContent(): List<FileSystemObjectHandle> = fileSystem.listDirectoryContent(this)

    override suspend fun createDirectory(name: String): DirectoryHandle? {
        TODO("not implemented")
    }

    override suspend fun createFile(name: String): FileHandle? {
        TODO("not implemented")
    }

}

data class FileHandleKorio(
    val fileSystem: FileSystemKorio,
    val handle: VfsFile
) : FileHandle {
    override val name: String get() = handle.pathInfo.baseName
    override val extension: String get() = name.substringAfterLast('.')

    override suspend fun readContent(): String? = fileSystem.readFileContent(this)
    override suspend fun writeContent(content: String) = fileSystem.writeFileContent(this, content)
    override suspend fun openAsZipDirectory(): DirectoryHandle = fileSystem.openFileAsZipDirectory(this)
}

object FileSystemKorio : FileSystem {

    suspend fun getEntry(parentDirectory: DirectoryHandle, name: String): FileSystemObjectHandle? {
        return when (parentDirectory) {
            is DirectoryHandleKorio -> {
                val child = parentDirectory.handle[name]
                when {
                    child.exists() && child.isDirectory() -> DirectoryHandleKorio(this, child)
                    child.exists() && child.isFile() -> FileHandleKorio(this, child)
                    else -> null //if not found in loop
                }
            }
            else -> null
        }
    }
/*
    suspend fun getDirectory(fullPath: String, mode: FileAccessMode): DirectoryHandle? {
        return selectDirectoryFromDialog(null, mode)
    }

    suspend fun selectDirectoryFromDialog(current: DirectoryHandle?, mode: FileAccessMode): DirectoryHandle? {
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

    suspend fun selectExistingFileFromDialog(current: DirectoryHandle?, mode: FileAccessMode, useNativeDialog: Boolean): FileHandle? {
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

    suspend fun selectNewFileFromDialog(parentDirectory: DirectoryHandle): FileHandle? {
        val p = (window as WasmWindow).showSaveFilePicker()
        return try {
            val handle: FileSystemFileHandle = p.await()
            FileHandleWasmJS(this, handle)
        } catch (t: Throwable) {
            null
        }
    }
*/
    suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle> =
        when (dir) {
            is DirectoryHandleKorio -> {
                val list = dir.handle.list().map { child ->
                    when {
                        child.isDirectory() -> DirectoryHandleKorio(this, child)
                        child.isFile() -> FileHandleKorio(this, child)
                        else -> error("Should not happen")
                    }
                }
                list.toList()
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${dir::class.simpleName}")
        }

    suspend fun createNewFile(parentPath: DirectoryHandle, name: String): FileHandle? {
        return when (parentPath) {
            is DirectoryHandleKorio -> {
                val newFile = parentPath.handle[name]
                newFile.writeString("") //creates the file
                FileHandleKorio(this, newFile)
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parentPath::class.simpleName}")
        }
    }

    suspend fun createNewDirectory(parentPath: DirectoryHandle, name: String): DirectoryHandle? {
        return when (parentPath) {
            is DirectoryHandleKorio -> {
                val newDir = parentPath.handle[name]
                newDir.mkdir()
                DirectoryHandleKorio(this, newDir)
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parentPath::class.simpleName}")
        }
    }

    suspend fun readFileContent(file: FileHandle): String? =
        when (file) {
            is FileHandleKorio -> {
                file.handle.readString()
            }

            else -> error("FileHandle is not a FileHandleJS: ${file::class.simpleName}")
        }

    suspend fun writeFileContent(file: FileHandle, content: String) {
        when (file) {
            is FileHandleKorio -> {
                file.handle.writeString(content)
            }

            else -> error("FileHandle is not a FileHandleJS: ${file::class.simpleName}")
        }
    }

    suspend fun openFileAsZipDirectory(file: FileHandle): DirectoryHandle {
        val handle = (file as FileHandleKorio).handle
        val zipFs = handle.openAsZip()
        return DirectoryHandleKorio(this, zipFs)
    }

}