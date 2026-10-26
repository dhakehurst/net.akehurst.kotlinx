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
import net.akehurst.kotlinx.filesystem.api.FileAccessMode
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.filesystem.api.FileSystemObjectHandle
import net.akehurst.kotlinx.filesystem.api.UserFileSystem

// the members are listed here because the common metadata compiler does not accept an expect object that leaves interface members unimplemented
expect object UserFileSystemDefault : UserFileSystem {
    override suspend fun getEntry(parent: DirectoryHandle, name: String): FileSystemObjectHandle?
    override suspend fun getDirectory(fullPath: String, mode: FileAccessMode): DirectoryHandle?
    override suspend fun selectDirectoryFromDialog(dialogTitle: String, current: DirectoryHandle?, accessMode: FileAccessMode): DirectoryHandle?
    override suspend fun selectExistingFileFromDialog(dialogTitle: String, current: DirectoryHandle?, accessMode: FileAccessMode, useNativeDialog: Boolean): FileHandle?
    override suspend fun selectNewFileFromDialog(dialogTitle: String, parent: DirectoryHandle): FileHandle?
    override suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle>
    override suspend fun createNewFile(parent: DirectoryHandle, name: String): FileHandle?
    override suspend fun createNewDirectory(parent: DirectoryHandle, name: String): DirectoryHandle?
    override suspend fun exists(entry: FileSystemObjectHandle): Boolean
    override suspend fun readFileContent(file: FileHandle): String?
    override suspend fun writeFileContent(file: FileHandle, content: String)
    override suspend fun openFileAsZipDirectory(file: FileHandle): DirectoryHandle?
}
