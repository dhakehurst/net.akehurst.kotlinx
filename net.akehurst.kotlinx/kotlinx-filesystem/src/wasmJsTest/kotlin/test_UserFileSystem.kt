package net.akehurst.kotlinx.filesystem

import korlibs.io.async.runBlockingNoJs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.test.Test

class test_UserFileSystem {

    @Test
    fun test_ReadStream() {
        runBlockingNoJs {
            val file = UserFileSystem.selectExistingFileFromDialog(null, FileAccessMode.READ_ONLY)

            val content = file?.readContent()

            println(content)
        }
    }

}