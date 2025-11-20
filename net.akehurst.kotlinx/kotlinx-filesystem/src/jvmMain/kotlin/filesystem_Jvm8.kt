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

import korlibs.io.file.std.ZipVfs
import korlibs.io.stream.openAsync
import net.akehurst.kotlinx.filesystem.api.DirectoryHandle
import net.akehurst.kotlinx.filesystem.api.FileHandle
import net.akehurst.kotlinx.filesystem.api.FileSystem
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

data class DirectoryHandleJVM(
    val fileSystem: UserFileSystem,
    override val parent: DirectoryHandleJVM?,
    val handle: File
) : DirectoryHandleAbstract() {

    override val name: String get() = handle.name
    override val absolutePath: String get() = handle.path

    override suspend fun entry(name: String): FileSystemObjectHandle? =
        fileSystem.getEntry(this, name)

    override suspend fun listContent(): List<FileSystemObjectHandle> =
        fileSystem.listDirectoryContent(this)

    override suspend fun createDirectory(name: String): DirectoryHandle? {
        return fileSystem.createNewDirectory(this, name)
    }

    override suspend fun createFile(name: String): FileHandle? {
        return fileSystem.createNewFile(this, name)
    }
}

data class FileHandleJVM(
    val fileSystem: UserFileSystem,
    override val parent: DirectoryHandleJVM?,
    val handle: File
) : FileHandleAbstract() {

    override val name: String get() = handle.name
    override val absolutePath: String get() = handle.path

    override suspend fun readContent(): String? = fileSystem.readFileContent(this)
    override suspend fun writeContent(content: String) = fileSystem.writeFileContent(this, content)
    override suspend fun openAsZipDirectory(): DirectoryHandle? = fileSystem.openFileAsZipDirectory(this)
}

actual object UserFileSystem : FileSystem {

    actual suspend fun getEntry(parent: DirectoryHandle, name: String): FileSystemObjectHandle? {
        return when (parent) {
            is DirectoryHandleJVM -> {
                val f = parent.handle.resolve(name)
                when {
                    f.exists().not() -> null
                    f.isDirectory -> DirectoryHandleJVM(parent.fileSystem, parent, f)
                    f.isFile -> FileHandleJVM(parent.fileSystem, parent, f)
                    else -> error("directory entry ${parent.path}/$name is neither a file nor a directory")
                }
            }

            else -> null
        }
    }

    actual suspend fun getDirectory(fullPath: String, mode: FileAccessMode): DirectoryHandle? {
        val file = File(fullPath)
        return if (file.exists() && file.isDirectory) {
            DirectoryHandleJVM(this, null, file)
        } else {
            null
        }
    }

    actual suspend fun selectDirectoryFromDialog(current: DirectoryHandle?, accessMode: FileAccessMode): DirectoryHandle? {
        try {
            return if (EventQueue.isDispatchThread()) {
                chooseDirectory2(current as DirectoryHandleJVM?)
            } else {
                var handle: DirectoryHandle? = null
                EventQueue.invokeAndWait() {
                    handle = chooseDirectory2(current as DirectoryHandleJVM?)
                }
                handle
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }

    private fun chooseDirectory2(current: DirectoryHandleJVM?): DirectoryHandle? {
        val parentFrame = Frame("Choose Directory")
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        val fd = FileDialog(parentFrame, "Choose Directory")
        fd.mode = FileDialog.LOAD
        fd.isMultipleMode = false
        fd.directory = current?.handle?.name
        fd.isVisible = true
        val selectedFile = fd.file?.let { File(fd.directory + "/" + it) }
        return selectedFile?.let {
            val pf: File? = selectedFile.parentFile
            val parent = pf?.let { DirectoryHandleJVM(this, null, pf) }
            DirectoryHandleJVM(this, parent, it)
        }
    }

    actual suspend fun selectExistingFileFromDialog(current: DirectoryHandle?, accessMode: FileAccessMode, useNativeDialog: Boolean): FileHandle? {
        try {
            return if (EventQueue.isDispatchThread()) {
                if (useNativeDialog) {
                    chooseFileNative(current as DirectoryHandleJVM?, FileDialog.LOAD)
                } else {
                    chooseFile(current as DirectoryHandleJVM?)
                }
            } else {
                var handle: FileHandle? = null
                EventQueue.invokeAndWait() {
                    handle = if (useNativeDialog) {
                        chooseFileNative(current as DirectoryHandleJVM?, FileDialog.LOAD)
                    } else {
                        chooseFile(current as DirectoryHandleJVM?)
                    }
                }
                handle
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }

    private fun chooseFile(current: DirectoryHandleJVM?): FileHandle? {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val fileChooser = JFileChooser()
        fileChooser.currentDirectory = current?.handle
        fileChooser.isMultiSelectionEnabled = false
        fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
        fileChooser.dialogTitle = "Choose File"
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

    private fun chooseFileNative(current: DirectoryHandleJVM?, mode: Int): FileHandle? {
        val parentFrame = Frame("Choose File")
        System.clearProperty("apple.awt.fileDialogForDirectories")
        val fd = FileDialog(parentFrame, "Choose File")
        fd.mode = mode
        fd.isMultipleMode = false
        fd.directory = current?.handle?.name
        fd.isVisible = true
        val selectedFile = fd.file?.let { File(fd.directory + "/" + it) }
        return selectedFile?.let {
            val pf: File? = selectedFile.parentFile
            val parent = pf?.let { DirectoryHandleJVM(this, null, pf) }
            FileHandleJVM(this, parent, it)
        }
    }

    actual suspend fun selectNewFileFromDialog(parent: DirectoryHandle): FileHandle? {
        val file = chooseFile(parent as DirectoryHandleJVM)
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

    actual suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle> {
        try {
            return when (dir) {
                is DirectoryHandleJVM -> {
                    val dirEntries = dir.handle.toPath().listDirectoryEntries()
                    dirEntries.map {
                        when {
                            it.isDirectory() -> DirectoryHandleJVM(this, dir, it.toFile())
                            it.isRegularFile() -> FileHandleJVM(this, dir, it.toFile())
                            else -> error("shoudl not happen")
                        }
                    }
                }

                else -> error("DirectoryHandle is not a DirectoryHandleJS: ${dir::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return emptyList()
        }
    }

    actual suspend fun createNewFile(parent: DirectoryHandle, name: String): FileHandle? {
        try {
            return when (parent) {
                is DirectoryHandleJVM -> {
                    val newFile = parent.handle.resolve(name)
                    newFile.createNewFile()
                    FileHandleJVM(this, parent, newFile)
                }

                else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parent::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }

    actual suspend fun createNewDirectory(parent: DirectoryHandle, name: String): DirectoryHandle? {
        try {
            return when (parent) {
                is DirectoryHandleJVM -> {
                    val newDir = parent.handle.resolve(name)
                    newDir.mkdirs()
                    DirectoryHandleJVM(this, parent, newDir)
                }

                else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parent::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }

    actual suspend fun readFileContent(file: FileHandle): String? {
        try {
            return when (file) {
                is FileHandleJVM -> {
                    file.handle.readText()
                }

                else -> error("DirectoryHandle is not a DirectoryHandleJS: ${file::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }

    actual suspend fun writeFileContent(file: FileHandle, content: String) {
        try {
            return when (file) {
                is FileHandleJVM -> {
                    file.handle.writeText(content)
                }

                else -> error("DirectoryHandle is not a DirectoryHandleJS: ${file::class.simpleName}")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    actual suspend fun openFileAsZipDirectory(file: FileHandle): DirectoryHandle? {
        try {
            val handle = (file as FileHandleJVM).handle
            val byteArray = handle.readBytes()
            val zipFs = ZipVfs(byteArray.openAsync())
            return DirectoryHandleKorio(FileSystemKorio, null, zipFs)
        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }

}
