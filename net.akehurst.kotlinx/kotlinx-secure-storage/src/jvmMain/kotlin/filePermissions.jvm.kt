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
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission

private fun localPath(file: FileHandle): Path? = try {
    val p = Paths.get(file.absolutePath)
    if (Files.isRegularFile(p)) p else null
} catch (_: InvalidPathException) {
    null
}

internal actual fun restrictToOwner(file: FileHandle) {
    val path = localPath(file) ?: return
    val posix = Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
    if (null != posix) {
        posix.setPermissions(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
    } else {
        // e.g. Windows: take read/write away from everyone, then give it back to the owner
        val f = path.toFile()
        f.setReadable(false, false)
        f.setWritable(false, false)
        f.setExecutable(false, false)
        f.setReadable(true, true)
        f.setWritable(true, true)
    }
}

internal actual fun deleteFromDisk(file: FileHandle): Boolean {
    val path = localPath(file) ?: return false
    return Files.deleteIfExists(path)
}
