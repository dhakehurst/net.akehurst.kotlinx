package net.akehurst.kotlinx.filesystem

expect object UserFileSystem : FileSystem{
    //  var useDispatcher: Boolean

    suspend fun getEntry(parentDirectory: DirectoryHandle, name:String):FileSystemObjectHandle?

    suspend fun selectDirectoryFromDialog(current: DirectoryHandle? = null, mode: FileAccessMode = FileAccessMode.READ_WRITE): DirectoryHandle?
    suspend fun selectExistingFileFromDialog(mode: FileAccessMode = FileAccessMode.READ_WRITE): FileHandle?
    suspend fun selectNewFileFromDialog(parentDirectory: DirectoryHandle): FileHandle?

    suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle>

    suspend fun createNewFile(parentPath: DirectoryHandle): FileHandle?
    suspend fun createNewDirectory(parentPath: DirectoryHandle): DirectoryHandle?

    suspend fun readFileContent(file: FileHandle): String?

    suspend fun writeFileContent(file: FileHandle, content: String)

}