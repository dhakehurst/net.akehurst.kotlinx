package net.akehurst.kotlinx.filesystem

import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.filesystem.api.FileSystem
import net.akehurst.kotlinx.filesystem.api.FileSystemObjectHandle

actual object UserFileSystem : FileSystem {
    //actual var useDispatcher: Boolean = false
    actual suspend fun getEntry(parent: DirectoryHandle, name: String): FileSystemObjectHandle? {
        TODO()
    }

    actual suspend fun getDirectory(fullPath: String, mode: FileAccessMode): DirectoryHandle? {
        return selectDirectoryFromDialog(null, mode)
    }

    actual suspend fun selectDirectoryFromDialog(current: DirectoryHandle?, mode: FileAccessMode): DirectoryHandle? {
        TODO()
    }

    actual suspend fun selectExistingFileFromDialog(current: DirectoryHandle?, mode: FileAccessMode, useNativeDialog:Boolean): FileHandle? {
        TODO()
    }

    actual suspend fun selectNewFileFromDialog(parent: DirectoryHandle): FileHandle? {
        TODO()
    }

    actual suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle> {
        TODO()
    }

    actual suspend fun createNewFile(parent: DirectoryHandle, name: String): FileHandle? {
        TODO()
    }

    actual suspend fun createNewDirectory(parent: DirectoryHandle, name: String): DirectoryHandle? {
        TODO()
    }

    actual suspend fun readFileContent(file: FileHandle): String? =
        TODO()

    actual suspend fun writeFileContent(file: FileHandle, content: String) {
        TODO()
    }

    actual suspend fun openFileAsZipDirectory(file: FileHandle):DirectoryHandle {
        TODO()
    }
}
