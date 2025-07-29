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
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.filesystem.api.FileSystem
import net.akehurst.kotlinx.filesystem.api.FileSystemObjectHandle

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