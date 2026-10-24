package net.akehurst.kotlinx.filesystem

import net.akehurst.kotlinx.filesystem.api.FileAccessMode

suspend fun main() {


        val file = UserFileSystemDefault.selectExistingFileFromDialog("Choose file",null, FileAccessMode.READ_ONLY)

        val content = file?.readContent()

        println(content)


}