package net.akehurst.kotlinx.filesystem

enum class FileAccessMode {
    READ_ONLY,
    READ_WRITE
}

interface FileSystem {
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

abstract class FileHandleAbstract : FileHandle {
    override val extension: String get() = name.substringAfterLast('.')
}