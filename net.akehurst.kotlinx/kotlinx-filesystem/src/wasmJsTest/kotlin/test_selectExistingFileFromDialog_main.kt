package net.akehurst.kotlinx.filesystem

suspend fun main() {


        val file = UserFileSystem.selectExistingFileFromDialog("Choose file",null, FileAccessMode.READ_ONLY)

        val content = file?.readContent()

        println(content)


}