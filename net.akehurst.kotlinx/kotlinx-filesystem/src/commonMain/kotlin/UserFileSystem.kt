package net.akehurst.kotlinx.filesystem

expect object UserFileSystem : FileSystem{
    //  var useDispatcher: Boolean

    suspend fun getEntry(parentDirectory: DirectoryHandle, name:String):FileSystemObjectHandle?

    /**
     * for JS/Wasm, this just calls the selectDirectoryFromDialog method
     */
    suspend fun getDirectory(fullPath:String, mode: FileAccessMode = FileAccessMode.READ_WRITE):DirectoryHandle?

    suspend fun selectDirectoryFromDialog(current: DirectoryHandle? = null, mode: FileAccessMode = FileAccessMode.READ_WRITE): DirectoryHandle?
    suspend fun selectExistingFileFromDialog(mode: FileAccessMode = FileAccessMode.READ_WRITE): FileHandle?
    suspend fun selectNewFileFromDialog(parentDirectory: DirectoryHandle): FileHandle?

    suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle>

    suspend fun createNewFile(parentPath: DirectoryHandle, name:String): FileHandle?
    suspend fun createNewDirectory(parentPath: DirectoryHandle, name:String): DirectoryHandle?

    suspend fun readFileContent(file: FileHandle): String?

    suspend fun writeFileContent(file: FileHandle, content: String)

}