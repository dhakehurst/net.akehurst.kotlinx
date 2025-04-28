package net.akehurst.kotlinx.filesystem

import kotlin.test.Test

class test_UserFileSystem {

    @Test
    fun test_ReadStream()  = suspend {
        val file = UserFileSystem.selectExistingFileFromDialog(FileAccessMode.READ_ONLY)

        val content = file?.readContent()

        println(content)
    }

}