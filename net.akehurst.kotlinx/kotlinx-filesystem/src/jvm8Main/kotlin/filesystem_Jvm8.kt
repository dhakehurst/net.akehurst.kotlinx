package net.akehurst.kotlinx.filesystem

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

    private fun chooseDirectory2(current: DirectoryHandleJVM?): DirectoryHandle? {
        val parentFrame = Frame("Choose Directory")
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        val fd = FileDialog(parentFrame, "Choose Directory")
        fd.mode = FileDialog.LOAD
        fd.isMultipleMode = false
        fd.directory = current?.handle?.name
        fd.isVisible = true
        val selectedFile = fd.file?.let { File(fd.directory + "/" + it) }
        return selectedFile?.let { DirectoryHandleJVM(this, it) }
    }

    actual suspend fun selectExistingFileFromDialog(current: DirectoryHandle?,mode: FileAccessMode, useNativeDialog:Boolean): FileHandle? {
        return if (EventQueue.isDispatchThread()) {
            if(useNativeDialog) {
                chooseFileNative(current as DirectoryHandleJVM?, FileDialog.LOAD)
            } else {
                chooseFile(current as DirectoryHandleJVM?)
            }
        } else {
            var handle: FileHandle? = null
            EventQueue.invokeAndWait() {
                handle = if(useNativeDialog) {
                    chooseFileNative(current as DirectoryHandleJVM?, FileDialog.LOAD)
                } else {
                    chooseFile(current as DirectoryHandleJVM?)
                }
            }
            handle
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
        fileChooser.fileSelectionMode =  JFileChooser.FILES_ONLY
        fileChooser.dialogTitle =  "Choose File"
        val result = fileChooser.showOpenDialog(null) // 'null' for parent component
        return if (result == JFileChooser.APPROVE_OPTION) {
            val selectedFile = fileChooser.selectedFile
            selectedFile?.let { FileHandleJVM(this, selectedFile) }
        } else {
            null
        }
    }

    private fun chooseFileNative(current: DirectoryHandleJVM?, mode:Int): FileHandle? {
        val parentFrame = Frame("Choose File")
        System.clearProperty("apple.awt.fileDialogForDirectories")
        val fd = FileDialog(parentFrame, "Choose File")
        fd.mode = mode
        fd.isMultipleMode = false
        fd.directory = current?.handle?.name
        fd.isVisible = true
        val selectedFile = fd.file?.let { File(fd.directory + "/" + it) }
        return selectedFile?.let { FileHandleJVM(this, it) }
    }

    actual suspend fun selectNewFileFromDialog(parentDirectory: DirectoryHandle): FileHandle? {
        val file = chooseFile(parentDirectory as DirectoryHandleJVM)
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
