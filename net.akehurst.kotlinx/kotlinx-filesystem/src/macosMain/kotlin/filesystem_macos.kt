package net.akehurst.kotlinx.filesystem

actual object UserFileSystem : FileSystem {
    //actual var useDispatcher: Boolean = false
    actual suspend fun getEntry(parentDirectory: DirectoryHandle, name: String): FileSystemObjectHandle? {
        TODO()
    }

    actual suspend fun getDirectory(fullPath: String, mode: FileAccessMode): DirectoryHandle? {
        return selectDirectoryFromDialog(null, mode)
    }

    actual suspend fun selectDirectoryFromDialog(current: DirectoryHandle?, mode: FileAccessMode): DirectoryHandle? {
        TODO()
    }

    actual suspend fun selectExistingFileFromDialog(mode: FileAccessMode): FileHandle? {
        TODO()
    }

    actual suspend fun selectNewFileFromDialog(parentDirectory: DirectoryHandle): FileHandle? {
        TODO()
    }

    actual suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle> {
        TODO()
    }

    actual suspend fun createNewFile(parentPath: DirectoryHandle, name: String): FileHandle? {
        TODO()
    }

    actual suspend fun createNewDirectory(parentPath: DirectoryHandle, name: String): DirectoryHandle? {
        TODO()
    }

    actual suspend fun readFileContent(file: FileHandle): String? =
        TODO()

    actual suspend fun writeFileContent(file: FileHandle, content: String) {
        TODO()
    }

}
