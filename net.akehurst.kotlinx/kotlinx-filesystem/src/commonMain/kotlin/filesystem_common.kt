package net.akehurst.kotlinx.filesystem

enum class FileAccessMode {
    READ_ONLY,
    READ_WRITE
}

expect object UserFileSystem {
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

interface AppFilesystem {
    val root: DirectoryHandle

    suspend fun getDirectory(resourcePath: String): DirectoryHandle?
    suspend fun getFile(resourcePath: String): FileHandle?
    suspend fun read(resourcePath: String): String
}

interface FileSystemObjectHandle {
    val name: String
}

interface FileHandle : FileSystemObjectHandle {
    val extension: String
    suspend fun readContent(): String?
    suspend fun writeContent(content: String)
}

interface DirectoryHandle : FileSystemObjectHandle {
    val path: String

    suspend fun listContent(): List<FileSystemObjectHandle>
    suspend fun entry(name: String): FileSystemObjectHandle?
    suspend fun file(name: String): FileHandle?
    suspend fun directory(name: String): DirectoryHandle?
    suspend fun createFile(name: String): FileHandle?
    suspend fun createDirectory(name: String): DirectoryHandle?
}


abstract class DirectoryHandleAbstract : DirectoryHandle {
    override suspend fun directory(name: String): DirectoryHandle? {
        val entry = entry(name)
        return when (entry) {
            is DirectoryHandle -> entry
            else -> null
        }
    }

    override suspend fun file(name: String): FileHandle? {
        val entry = entry(name)
        return when (entry) {
            is FileHandle -> entry
            else -> null
        }
    }
}