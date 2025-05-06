package net.akehurst.kotlinx.filesystem

import korlibs.io.file.VfsFile
import korlibs.io.file.baseName
import korlibs.io.file.std.jailedLocalVfs
import korlibs.io.file.std.resourcesVfs
import korlibs.io.file.std.userHomeVfs

class UserHomeFileSystem(path: String) : FileSystemFromVfs(userHomeVfs[path].jail())
object ResourcesFileSystem : FileSystemFromVfs(resourcesVfs)

open class FileSystemFromVfs(
    private val _vfsRoot: VfsFile
) :FileSystem {

    constructor(path:String) : this(jailedLocalVfs(path))

    val root get() = DirectoryHandleVfs(this, _vfsRoot)

    suspend fun getDirectory(resourcePath: String): DirectoryHandle? {
        return _vfsRoot[resourcePath].takeIfExists()?.let {
            DirectoryHandleVfs(this, it)
        }
    }

    suspend fun getFile(resourcePath: String): FileHandle? {
        return _vfsRoot[resourcePath].takeIfExists()?.let {
            FileHandleVfs(this, it)
        }
    }

    suspend fun read(resourcePath: String): String {
        return _vfsRoot[resourcePath].readString()
    }
}

class DirectoryHandleVfs(
    val filesystem: FileSystemFromVfs,
    private val _handle: VfsFile
) : DirectoryHandleAbstract() {

    override val path: String get() = _handle.path

    override val name: String get() = _handle.pathInfo.baseName

    override suspend fun listContent(): List<FileSystemObjectHandle> {
        return when {
            _handle.isFile() -> emptyList()
            _handle.isDirectory() -> _handle.listNames().map {
                entry(it) ?: error("Not found entry: $it")
            }

            else -> emptyList()
        }
    }

    override suspend fun entry(name: String): FileSystemObjectHandle? {
        return _handle[name].takeIfExists()?.let {
            when {
                it.isDirectory() -> DirectoryHandleVfs(filesystem, it)
                it.isFile() -> FileHandleVfs(filesystem, it)
                else -> null
            }
        }
    }

    override suspend fun createDirectory(name: String): DirectoryHandle? {
        TODO("not implemented")
    }

    override suspend fun createFile(name: String): FileHandle? {
        _handle[name].ensureParents()
        _handle[name].writeString("")
        return FileHandleVfs(filesystem, _handle[name])
    }
}

class FileHandleVfs(
    val filesystem: FileSystemFromVfs,
    private val _handle: VfsFile
) : FileHandleAbstract() {

    override val name: String get() = _handle.pathInfo.baseName

    override suspend fun readContent(): String? =
        _handle.readString()

    override suspend fun writeContent(content: String) {
        _handle.writeString(content)
    }
}