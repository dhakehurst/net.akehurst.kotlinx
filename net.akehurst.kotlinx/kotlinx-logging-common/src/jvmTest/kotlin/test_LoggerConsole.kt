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

package net.akehurst.kotlinx.logging.common

import net.akehurst.kotlinx.logging.api.LogLevel
import net.akehurst.kotlinx.logging.api.LoggingManager
import net.akehurst.kotlinx.logging.api.logger
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class test_LoggerConsole {

    // runs the block with System.out captured, and returns what was printed
    private fun captureStdOut(block: () -> Unit): String {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return buffer.toString()
    }

    @Test
    fun level_change_applies_to_existing_logger() {
        LoggingManager.use(LoggingByConsole, LogLevel.Information)
        val logger = logger("test")

        val out = captureStdOut {
            logger.logDebug { "before-level-change" }
            LoggingManager.rootLoggingLevel = LogLevel.Debug
            logger.logDebug { "after-level-change" }
        }

        assertFalse(out.contains("before-level-change"))
        assertTrue(out.contains("Debug: test - after-level-change"))
    }

    @Test
    fun throwable_is_printed() {
        LoggingManager.use(LoggingByConsole, LogLevel.Information)
        val logger = logger("test")

        val out = captureStdOut {
            logger.logError(IllegalStateException("bad")) { "with-throwable" }
        }

        assertTrue(out.contains("Error: test - with-throwable"))
        assertTrue(out.contains("java.lang.IllegalStateException: bad"))
        assertTrue(out.contains("at ${test_LoggerConsole::class.java.name}.throwable_is_printed"))
    }

}
