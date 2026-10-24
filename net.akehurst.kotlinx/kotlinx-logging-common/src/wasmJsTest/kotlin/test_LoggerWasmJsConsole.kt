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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// replace console.error with a function that records the arguments of each call
private fun installConsoleErrorCapture(): Unit =
    js("{ globalThis.__testCaptured = []; globalThis.__testOriginalError = console.error; console.error = function() { globalThis.__testCaptured.push(Array.prototype.slice.call(arguments).map(String)); }; }")

private fun restoreConsoleError(): Unit = js("{ console.error = globalThis.__testOriginalError; }")
private fun capturedCallCount(): Int = js("globalThis.__testCaptured.length")
private fun capturedArgCount(call: Int): Int = js("globalThis.__testCaptured[call].length")
private fun capturedArg(call: Int, arg: Int): String = js("globalThis.__testCaptured[call][arg]")

class test_LoggerWasmJsConsole {

    private fun captureConsoleError(block: () -> Unit) {
        installConsoleErrorCapture()
        try {
            block()
        } finally {
            restoreConsoleError()
        }
    }

    @Test
    fun throwable_is_passed_to_console_with_message() {
        LoggingManager.use(LoggingByBrowserConsoleWasm, LogLevel.Information)
        val logger = logger("test")

        captureConsoleError {
            logger.logError(IllegalStateException("bad")) { "with-throwable" }
        }

        assertEquals(1, capturedCallCount())
        assertEquals(2, capturedArgCount(0))
        assertEquals("Error: test - with-throwable", capturedArg(0, 0))
        assertTrue(capturedArg(0, 1).contains("IllegalStateException: bad"), capturedArg(0, 1))
    }

    @Test
    fun no_throwable_passes_message_only() {
        LoggingManager.use(LoggingByBrowserConsoleWasm, LogLevel.Information)
        val logger = logger("test")

        captureConsoleError {
            logger.logError { "no-throwable" }
        }

        assertEquals(1, capturedCallCount())
        assertEquals(1, capturedArgCount(0))
        assertEquals("Error: test - no-throwable", capturedArg(0, 0))
    }

}
