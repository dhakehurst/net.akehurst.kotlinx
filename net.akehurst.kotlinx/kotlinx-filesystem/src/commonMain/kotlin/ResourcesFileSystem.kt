/**
 * Copyright (C) 2025 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.akehurst.kotlinx.filesystem

import korlibs.io.file.VfsFile
import korlibs.io.file.baseName
import korlibs.io.file.std.jailedLocalVfs
import korlibs.io.file.std.resourcesVfs
import korlibs.io.file.std.userHomeVfs
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.filesystem.api.FileSystem
import net.akehurst.kotlinx.filesystem.api.FileSystemObjectHandle

class UserHomeFileSystem(path: String) : FileSystemFromVfs(userHomeVfs[path].jail())
object ResourcesFileSystem : FileSystemFromVfs(resourcesVfs)

class DirectoryHandleVfs(
    val filesystem: FileSystemFromVfs,
    override val parent: DirectoryHandleVfs?,
    private val _handle: VfsFile
) : DirectoryHandleAbstract() {

    override val name: String get() = _handle.pathInfo.baseName
    override val absolutePath: String get() = _handle.absolutePath

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
                it.isDirectory() -> DirectoryHandleVfs(filesystem, this,it)
                it.isFile() -> FileHandleVfs(filesystem, this,it)
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
        return FileHandleVfs(filesystem, this,_handle[name])
    }
}

class FileHandleVfs(
    val filesystem: FileSystemFromVfs,
    override val parent: DirectoryHandleVfs?,
    private val _handle: VfsFile
) : FileHandleAbstract() {

    override val name: String get() = _handle.pathInfo.baseName
    override val absolutePath: String get() = _handle.absolutePath

    override suspend fun readContent(): String? = _handle.readString()

    override suspend fun writeContent(content: String)=_handle.writeString(content)
    override suspend fun openAsZipDirectory(): DirectoryHandle {
        TODO("not implemented")
    }
}

open class FileSystemFromVfs(
    private val _vfsRoot: VfsFile
) :FileSystem {

    constructor(path:String) : this(jailedLocalVfs(path))

    val root get() = DirectoryHandleVfs(this, null,_vfsRoot)

    suspend fun getDirectory(resourcePath: String): DirectoryHandle? {
        return _vfsRoot[resourcePath].takeIfExists()?.let {
            DirectoryHandleVfs(this, null,it)
        }
    }

    suspend fun getFile(resourcePath: String): FileHandle? {
        return _vfsRoot[resourcePath].takeIfExists()?.let {
            FileHandleVfs(this, null,it)
        }
    }

    suspend fun read(resourcePath: String): String {
        return _vfsRoot[resourcePath].readString()
    }
}
