/**
 * Copyright (C) 2026 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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


package net.akehurst.kotlinx.secure.storage

import net.akehurst.kotlinx.filesystem.api.FileHandle

/**
 * replace the content of [file], making sure first that only the owner can read or write it (where the platform supports that).
 * The file is created empty, then restricted, then written, so the content is never readable by others.
 */
internal suspend fun writeOwnerOnly(file: FileHandle, content: String) {
    if (file.exists().not()) file.writeContent("")
    restrictToOwner(file)
    file.writeContent(content)
}

/**
 * delete the file, or if that is not possible on this platform, make it empty.
 */
internal suspend fun deleteOrEmpty(file: FileHandle) {
    if (deleteFromDisk(file).not() && file.exists()) file.writeContent("")
}

/**
 * make the file readable and writable by its owner only (POSIX 600 where supported).
 * Does nothing if the file is not on a local disk, or the platform has no file permissions.
 */
internal expect fun restrictToOwner(file: FileHandle)

/** delete the file from the local disk; false if it is not on a local disk or cannot be deleted */
internal expect fun deleteFromDisk(file: FileHandle): Boolean
