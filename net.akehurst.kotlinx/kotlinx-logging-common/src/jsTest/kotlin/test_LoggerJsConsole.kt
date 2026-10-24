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
import kotlin.test.assertSame

class test_LoggerJsConsole {

    // runs the block with console.error replaced, and returns the arguments of each call
    private fun captureConsoleError(block: () -> Unit): List<List<Any?>> {
        val calls = mutableListOf<List<Any?>>()
        val c = console.asDynamic()
        val original = c.error
        val capture: (Array<Any?>) -> Unit = { args -> calls.add(args.toList()) }
        // wrap capture in a JS function that passes on all its arguments as an array
        val wrap: dynamic = js("(function(f){ return function(){ f(Array.prototype.slice.call(arguments)); }; })")
        c.error = wrap(capture)
        try {
            block()
        } finally {
            c.error = original
        }
        return calls
    }

    @Test
    fun throwable_is_passed_to_console_with_message() {
        LoggingManager.use(LoggingByBrowserConsole, LogLevel.Information)
        val logger = logger("test")
        val ex = IllegalStateException("bad")

        val calls = captureConsoleError {
            logger.logError(ex) { "with-throwable" }
        }

        assertEquals(1, calls.size)
        assertEquals(2, calls[0].size)
        assertEquals("Error: test - with-throwable", calls[0][0])
        assertSame<Any?>(ex, calls[0][1])
    }

    @Test
    fun no_throwable_passes_message_only() {
        LoggingManager.use(LoggingByBrowserConsole, LogLevel.Information)
        val logger = logger("test")

        val calls = captureConsoleError {
            logger.logError { "no-throwable" }
        }

        assertEquals(listOf<Any?>("Error: test - no-throwable"), calls.single())
    }

}
