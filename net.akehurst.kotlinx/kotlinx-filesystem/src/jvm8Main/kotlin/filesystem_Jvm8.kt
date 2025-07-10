package net.akehurst.kotlinx.filesystem

import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import kotlin.collections.map
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.readText
import kotlin.io.resolve
import kotlin.io.writeText
import kotlin.let
import kotlin.text.substringAfterLast

actual object UserFileSystem : FileSystem {

    actual suspend fun getEntry(parentDirectory: DirectoryHandle, name: String): FileSystemObjectHandle? {
        return when (parentDirectory) {
            is DirectoryHandleJVM -> {
                val f = parentDirectory.handle.resolve(name)
                FileHandleJVM(parentDirectory.fileSystem, f)
            }

            else -> null
        }
    }

    actual suspend fun getDirectory(fullPath:String, mode: FileAccessMode):DirectoryHandle? {
        val file = File(fullPath)
        return if(file.exists() && file.isDirectory) {
            DirectoryHandleJVM(this,file)
        } else {
            null
        }
    }

    actual suspend fun selectDirectoryFromDialog(current: DirectoryHandle?,mode: FileAccessMode): DirectoryHandle? {
        return if (EventQueue.isDispatchThread()) {
            chooseDirectory2(current as DirectoryHandleJVM?)
        } else {
            var handle: DirectoryHandle? = null
            EventQueue.invokeAndWait() {
                handle = chooseDirectory2(current as DirectoryHandleJVM?)
            }
            handle
        }
    }

    private fun chooseDirectory(current: DirectoryHandleJVM?): DirectoryHandle? {
        val fc = JFileChooser()
        current?.let { fc.selectedFile = current.handle }
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
        fc.setAcceptAllFileFilterUsed(false)
        fc.isFileHidingEnabled = false
        return if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            DirectoryHandleJVM(this, fc.selectedFile)
        } else {
            null
        }
    }

    private fun chooseDirectory2(current: DirectoryHandleJVM?): DirectoryHandle? {
        val parentFrame = Frame("Choose Directory")
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        val fd = FileDialog(parentFrame, "Choose directory")
        fd.directory = current?.handle?.name
        fd.isVisible = true
        val selectedFile = fd.file?.let { File(fd.directory + "/" + it) }
        return selectedFile?.let { DirectoryHandleJVM(this, it) }
    }

    actual suspend fun selectExistingFileFromDialog(mode: FileAccessMode): FileHandle? {
        val fc = JFileChooser()
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY)
        fc.setAcceptAllFileFilterUsed(false)
        return if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            FileHandleJVM(this, fc.selectedFile)
        } else {
            null
        }
    }

    actual suspend fun selectNewFileFromDialog(parentDirectory: DirectoryHandle): FileHandle? {
        val fc = JFileChooser()
        fc.currentDirectory = (parentDirectory as DirectoryHandleJVM).handle
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY)
        fc.setAcceptAllFileFilterUsed(false)
        return if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            val file = fc.selectedFile
            when {
                file.exists() -> Unit
                else -> file.createNewFile()
            }
            FileHandleJVM(this, file)
        } else {
            null
        }
    }

    actual suspend fun listDirectoryContent(dir: DirectoryHandle): List<FileSystemObjectHandle> {
        return when (dir) {
            is DirectoryHandleJVM -> {
                val dirEntries = dir.handle.toPath().listDirectoryEntries()
                dirEntries.map {
                    when {
                        it.isDirectory() -> DirectoryHandleJVM(this, it.toFile())
                        it.isRegularFile() -> FileHandleJVM(this, it.toFile())
                        else -> error("shoudl not happen")
                    }
                }
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${dir::class.simpleName}")
        }
    }

    actual suspend fun createNewFile(parentPath: DirectoryHandle, name:String): FileHandle? {
        return when (parentPath) {
            is DirectoryHandleJVM -> {
                val newFile = parentPath.handle.resolve(name)
                newFile.createNewFile()
                FileHandleJVM(this, newFile)
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parentPath::class.simpleName}")
        }
    }

    actual suspend fun createNewDirectory(parentPath: DirectoryHandle, name:String): DirectoryHandle? {
        return when (parentPath) {
            is DirectoryHandleJVM -> {
                val newDir = parentPath.handle.resolve(name)
                newDir.mkdirs()
                DirectoryHandleJVM(this, newDir)
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${parentPath::class.simpleName}")
        }
    }

    actual suspend fun readFileContent(file: FileHandle): String? {
        return when (file) {
            is FileHandleJVM -> {
                file.handle.readText()
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${file::class.simpleName}")
        }
    }

    actual suspend fun writeFileContent(file: FileHandle, content: String) {
        return when (file) {
            is FileHandleJVM -> {
                file.handle.writeText(content)
            }

            else -> error("DirectoryHandle is not a DirectoryHandleJS: ${file::class.simpleName}")
        }
    }

}

data class DirectoryHandleJVM(
    val fileSystem: UserFileSystem,
    val handle: File
) : DirectoryHandleAbstract() {

    override val name: String get() = handle.name

    override val path: String get() = handle.path

    override suspend fun entry(name: String): FileSystemObjectHandle? =
        fileSystem.getEntry(this, name)


    override suspend fun listContent(): List<FileSystemObjectHandle> =
        fileSystem.listDirectoryContent(this)

    override suspend fun createDirectory(name: String): DirectoryHandle? {
       return fileSystem.createNewDirectory(this,name)
    }

    override suspend fun createFile(name: String): FileHandle? {
        return fileSystem.createNewFile(this,name)
    }
}

data class FileHandleJVM(
    val fileSystem: UserFileSystem,
    val handle: File
) : FileHandleAbstract() {

    override val name: String get() = handle.name

    override suspend fun readContent(): String? =
        fileSystem.readFileContent(this)

    override suspend fun writeContent(content: String) =
        fileSystem.writeFileContent(this, content)
}
