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

actual object UserFileSystem : FileSystem {
    //actual var useDispatcher: Boolean = false
    actual suspend fun getEntry(parent: DirectoryHandle, name: String): FileSystemObjectHandle? = TODO()
    actual suspend fun exists(entry: FileSystemObjectHandle): Boolean = TODO()
    actual suspend fun getDirectory(fullPath: String, mode: FileAccessMode): DirectoryHandle? = TODO()
    actual suspend fun selectDirectoryFromDialog(dialogTitle: String, current: DirectoryHandle?, accessMode: FileAccessMode): DirectoryHandle? = TODO()
    actual suspend fun selectExistingFileFromDialog(dialogTitle: String, current: DirectoryHandle?, accessMode: FileAccessMode, useNativeDialog: Boolean): FileHandle? = TODO()
    actual suspend fun selectNewFileFromDialog(dialogTitle: String, parent: DirectoryHandle): FileHandle? = TODO()
    actual suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle> = TODO()
    actual suspend fun createNewFile(parent: DirectoryHandle, name: String): FileHandle? = TODO()
    actual suspend fun createNewDirectory(parent: DirectoryHandle, name: String): DirectoryHandle? = TODO()
    actual suspend fun readFileContent(file: FileHandle): String? = TODO()
    actual suspend fun writeFileContent(file: FileHandle, content: String): Unit = TODO()
    actual suspend fun openFileAsZipDirectory(file: FileHandle): DirectoryHandle? = TODO()
}
