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
import net.akehurst.kotlinx.filesystem.api.FileAccessMode
import net.akehurst.kotlinx.filesystem.api.FileMetadata
import net.akehurst.kotlinx.filesystem.api.UserFileSystem
import net.akehurst.kotlinx.filesystem.api.FileSystemObjectHandle
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.printStackTrace
import kotlin.time.Instant

data class DirectoryHandleJVM(
    val fileSystem: UserFileSystemDefault,
    override val parent: DirectoryHandleJVM?,
    val handle: File
) : DirectoryHandleAbstract() {

    override val name: String get() = handle.name
    override val absolutePath: String get() = handle.absolutePath

    override suspend fun exists(): Boolean = fileSystem.exists(this)
    override suspend fun entry(name: String): FileSystemObjectHandle? = fileSystem.getEntry(this, name)
    override suspend fun listContent(): List<FileSystemObjectHandle> = fileSystem.listDirectoryContent(this)
    override suspend fun createDirectory(name: String): DirectoryHandle? = fileSystem.createNewDirectory(this, name)
    override suspend fun createFile(name: String): FileHandle? = fileSystem.createNewFile(this, name)
}

data class FileHandleJVM(
    val fileSystem: UserFileSystemDefault,
    override val parent: DirectoryHandleJVM?,
    val handle: File
) : FileHandleAbstract() {

    override val name: String get() = handle.name
    override val absolutePath: String get() = handle.absolutePath

    override suspend fun exists(): Boolean = fileSystem.exists(this)
    override suspend fun readContent(): String? = fileSystem.readFileContent(this)
    override suspend fun readBytes(): ByteArray? = fileSystem.readFileBytes(this)
    override suspend fun writeContent(content: String) = fileSystem.writeFileContent(this, content)
    override suspend fun writeBytes(content: ByteArray) = fileSystem.writeFileBytes(this, content)
    override suspend fun metadata(): FileMetadata = when {
        handle.isFile -> FileMetadata(
            size = handle.length(),
            // File.lastModified() is 0 when unknown
            lastModified = handle.lastModified().takeIf { it > 0L }?.let { Instant.fromEpochMilliseconds(it) }
        )
        else -> FileMetadata.UNKNOWN
    }
    override suspend fun openAsZipDirectory(): DirectoryHandle = fileSystem.openFileAsZipDirectory(this)
}

actual object UserFileSystemDefault : UserFileSystem {

    actual override suspend fun getEntry(parent: DirectoryHandle, name: String): FileSystemObjectHandle? {
        return when {
            name.isEmpty() -> parent
            parent is DirectoryHandleJVM -> {
                val f = parent.handle.resolve(name)
                when {
                    f.exists().not() -> null
                    f.isDirectory -> DirectoryHandleJVM(parent.fileSystem, parent, f)
                    f.isFile -> FileHandleJVM(parent.fileSystem, parent, f)
                    else -> null // neither a file nor a directory (e.g. FIFO, socket); listDirectoryContent skips these too
                }
            }

            else -> null
        }
    }

    actual override suspend fun getDirectory(fullPath: String, mode: FileAccessMode): DirectoryHandle? {
        val file = File(fullPath)
        return if (file.exists() && file.isDirectory) {
            DirectoryHandleJVM(this, null, file)
        } else {
            null
        }
    }

    private val isMacOS: Boolean by lazy {
        System.getProperty("os.name")?.lowercase()?.startsWith("mac") ?: false
    }

    /**
     * On macOS this uses the native java.awt.FileDialog (with apple.awt.fileDialogForDirectories=true),
     * on all other OSs it uses javax.swing.JFileChooser in DIRECTORIES_ONLY mode,
     * because the AWT FileDialog on Linux (GTK) and Windows cannot select a directory.
     * The dialog is always shown on the AWT event dispatch thread.
     */
    actual override suspend fun selectDirectoryFromDialog(dialogTitle:String, current: DirectoryHandle?, accessMode: FileAccessMode): DirectoryHandle? {
        try {
            val chooser: () -> DirectoryHandle? = {
                if (isMacOS) {
                    chooseDirectoryNative(dialogTitle, current as DirectoryHandleJVM?)
                } else {
                    chooseDirectorySwing(dialogTitle, current as DirectoryHandleJVM?)
                }
            }
            return if (EventQueue.isDispatchThread()) {
                chooser()
            } else {
                var handle: DirectoryHandle? = null
                EventQueue.invokeAndWait() {
                    handle = chooser()
                }
                handle
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }

    private fun chooseDirectorySwing(dialogTitle:String, current: DirectoryHandleJVM?): DirectoryHandle? {
        val fileChooser = JFileChooser()
        fileChooser.currentDirectory = current?.handle
        fileChooser.isMultiSelectionEnabled = false
        fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        fileChooser.dialogTitle = dialogTitle
        val result = fileChooser.showOpenDialog(null) // 'null' for parent component
        return if (result == JFileChooser.APPROVE_OPTION) {
            fileChooser.selectedFile?.let { selectedDir ->
                val pf: File? = selectedDir.parentFile
                val parent = pf?.let { DirectoryHandleJVM(this, null, pf) }
                DirectoryHandleJVM(this, parent, selectedDir)
            }
        } else {
            null
        }
    }

    // macOS only: apple.awt.fileDialogForDirectories has no effect on other OSs
    private fun chooseDirectoryNative(dialogTitle:String, current: DirectoryHandleJVM?): DirectoryHandle? {
        val parentFrame = Frame(dialogTitle)
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        val fd = FileDialog(parentFrame, dialogTitle)
        fd.mode = FileDialog.LOAD
        fd.isMultipleMode = false
        fd.directory = current?.handle?.absolutePath
        fd.isVisible = true
        val selectedFile = fd.file?.let { File(fd.directory + "/" + it) }
        return selectedFile?.let {
            val pf: File? = selectedFile.parentFile
            val parent = pf?.let { DirectoryHandleJVM(this, null, pf) }
            DirectoryHandleJVM(this, parent, it)
        }
    }

    actual override suspend fun selectExistingFileFromDialog(dialogTitle:String, current: DirectoryHandle?, accessMode: FileAccessMode, useNativeDialog: Boolean): FileHandle? {
        try {
            return if (EventQueue.isDispatchThread()) {
                if (useNativeDialog) {
                    chooseFileNative(dialogTitle,current as DirectoryHandleJVM?, FileDialog.LOAD)
                } else {
                    chooseFile(dialogTitle,current as DirectoryHandleJVM?)
                }
            } else {
                var handle: FileHandle? = null
                EventQueue.invokeAndWait() {
                    handle = if (useNativeDialog) {
                        chooseFileNative(dialogTitle,current as DirectoryHandleJVM?, FileDialog.LOAD)
                    } else {
                        chooseFile(dialogTitle,current as DirectoryHandleJVM?)
                    }
                }
                handle
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }

    private fun chooseFile(dialogTitle:String,current: DirectoryHandleJVM?): FileHandle? {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val fileChooser = JFileChooser()
        fileChooser.currentDirectory = current?.handle
        fileChooser.isMultiSelectionEnabled = false
        fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
        fileChooser.dialogTitle = dialogTitle
        val result = fileChooser.showOpenDialog(null) // 'null' for parent component
        return if (result == JFileChooser.APPROVE_OPTION) {
            val selectedFile = fileChooser.selectedFile
            selectedFile?.let {
                val pf: File? = selectedFile.parentFile
                val parent = pf?.let { DirectoryHandleJVM(this, null, pf) }
                FileHandleJVM(this, parent, selectedFile)
            }
        } else {
            null
        }
    }

    private fun chooseFileNative(dialogTitle:String,current: DirectoryHandleJVM?, mode: Int): FileHandle? {
        val parentFrame = Frame(dialogTitle)
        System.clearProperty("apple.awt.fileDialogForDirectories")
        val fd = FileDialog(parentFrame, dialogTitle)
        fd.mode = mode
        fd.isMultipleMode = false
        fd.directory = current?.handle?.absolutePath
        fd.isVisible = true
        val selectedFile = fd.file?.let { File(fd.directory + "/" + it) }
        return selectedFile?.let {
            val pf: File? = selectedFile.parentFile
            val parent = pf?.let { DirectoryHandleJVM(this, null, pf) }
            FileHandleJVM(this, parent, it)
        }
    }

    actual override suspend fun selectNewFileFromDialog(dialogTitle:String, parent: DirectoryHandle): FileHandle? {
        val file = chooseFile(dialogTitle,parent as DirectoryHandleJVM)
        return if (null != file) {
            val jFile = (file as FileHandleJVM).handle
            when {
                jFile.exists() -> Unit
                else -> jFile.createNewFile()
            }
            file
        } else {
            null
        }
    }

    actual override suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle> {
        try {
            return when (dir) {
                is DirectoryHandleJVM -> {
                    val dirEntries = dir.handle.toPath().listDirectoryEntries()
                    // skip entries that are neither a directory nor a regular file (e.g. broken symlinks)
                    dirEntries.mapNotNull {
                        when {
                            it.isDirectory() -> DirectoryHandleJVM(this, dir, it.toFile())
                            it.isRegularFile() -> FileHandleJVM(this, dir, it.toFile())
                            else -> null
                        }
                    }
                }

                else -> error("DirectoryHandle is not a DirectoryHandleJVM: ${dir::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return emptyList()
        }
    }

    actual override suspend fun createNewFile(parent: DirectoryHandle, name: String): FileHandle? {
        try {
            return when (parent) {
                is DirectoryHandleJVM -> {
                    val newFile = parent.handle.resolve(name)
                    newFile.createNewFile() // does nothing if something already has that name
                    if (newFile.isFile) FileHandleJVM(this, parent, newFile) else null
                }

                else -> error("DirectoryHandle is not a DirectoryHandleJVM: ${parent::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }

    actual override suspend fun createNewDirectory(parent: DirectoryHandle, name: String): DirectoryHandle? {
        try {
            return when (parent) {
                is DirectoryHandleJVM -> {
                    val newDir = parent.handle.resolve(name)
                    newDir.mkdirs() // does nothing if something already has that name
                    if (newDir.isDirectory) DirectoryHandleJVM(this, parent, newDir) else null
                }

                else -> error("DirectoryHandle is not a DirectoryHandleJVM: ${parent::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }

    actual override suspend fun exists(entry: FileSystemObjectHandle): Boolean {
        return when (entry) {
            is DirectoryHandleJVM -> entry.handle.exists()
            is FileHandleJVM -> entry.handle.exists()
            else -> error("entry is not a FileHandleJVM or a DirectoryHandleJVM: ${entry::class.simpleName}")
        }
    }

    actual override suspend fun readFileContent(file: FileHandle): String? {
        try {
            return when (file) {
                is FileHandleJVM -> {
                    file.handle.readText()
                }

                else -> error("FileHandle is not a FileHandleJVM: ${file::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }

    actual override suspend fun readFileBytes(file: FileHandle): ByteArray? {
        try {
            return when (file) {
                is FileHandleJVM -> if (file.handle.isFile) file.handle.readBytes() else null
                else -> error("FileHandle is not a FileHandleJVM: ${file::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }

    actual override suspend fun writeFileBytes(file: FileHandle, content: ByteArray) {
        try {
            when (file) {
                is FileHandleJVM -> file.handle.writeBytes(content)
                else -> error("FileHandle is not a FileHandleJVM: ${file::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    actual override suspend fun writeFileContent(file: FileHandle, content: String) {
        try {
            return when (file) {
                is FileHandleJVM -> {
                    file.handle.writeText(content)
                }

                else -> error("FileHandle is not a FileHandleJVM: ${file::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    actual override suspend fun openFileAsZipDirectory(file: FileHandle): DirectoryHandle {
        val handle = (file as? FileHandleJVM)?.handle ?: error("FileHandle is not a FileHandleJVM: ${file::class.simpleName}")
        return ZipFileSystem.open(handle.readBytes(), handle.name)
    }

}
