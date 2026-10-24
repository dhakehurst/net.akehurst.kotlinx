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
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.WriterAppender
import org.apache.logging.log4j.core.layout.PatternLayout
import java.io.StringWriter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class test_LoggerLog4JLocation {

    private val appenderName = "test_LoggerLog4JLocation"
    private val output = StringWriter()

    @BeforeTest
    fun before() {
        val context = LogManager.getContext(false) as LoggerContext
        val appender = WriterAppender.newBuilder()
            .setName(appenderName)
            .setTarget(output)
            .setLayout(PatternLayout.newBuilder().withPattern("%C.%M %m%n").build())
            .build()
        appender.start()
        context.configuration.rootLogger.addAppender(appender, null, null)
        context.updateLoggers()
        LoggingManager.use(LoggingByLog4J, LogLevel.Trace)
    }

    @AfterTest
    fun after() {
        val context = LogManager.getContext(false) as LoggerContext
        context.configuration.rootLogger.removeAppender(appenderName)
        context.updateLoggers()
    }

    // the location log4j reports for the line containing the given message
    private fun locationOf(message: String): String {
        val line = output.toString().lines().first { it.endsWith(" $message") }
        return line.removeSuffix(" $message")
    }

    @Test
    fun location_is_the_calling_code() {
        val expected = "${test_LoggerLog4JLocation::class.java.name}.location_is_the_calling_code"
        val logger = logger("test")

        logger.log(LogLevel.Information) { "direct" }
        logger.logInformation { "info" }
        logger.logError(RuntimeException("boom")) { "error-with-throwable" }
        logger.logDebugIf({ true }) { "debugIf" }
        logger.logIf({ true }, LogLevel.Warning) { "logIf" }

        assertEquals(expected, locationOf("direct"))
        assertEquals(expected, locationOf("info"))
        assertEquals(expected, locationOf("error-with-throwable"))
        assertEquals(expected, locationOf("debugIf"))
        assertEquals(expected, locationOf("logIf"))
        assertTrue(output.toString().contains("java.lang.RuntimeException: boom"))
    }

    @Test
    fun disabled_level_is_not_logged() {
        LoggingManager.rootLoggingLevel = LogLevel.Information
        val logger = logger("test")

        logger.logDebug { "hidden" }

        assertTrue(output.toString().lines().none { it.endsWith(" hidden") })
    }

    @Test
    fun use_without_level_keeps_framework_level() {
        LoggingManager.rootLoggingLevel = LogLevel.Warning

        LoggingManager.use(LoggingByLog4J)

        assertEquals(LogLevel.Warning, LoggingManager.rootLoggingLevel)
    }

    @Test
    fun use_logs_framework_at_debug() {
        LoggingManager.use(LoggingByLog4J, LogLevel.Information)
        output.buffer.setLength(0) // drop the line logged by before()
        LoggingManager.use(LoggingByLog4J)
        assertTrue(output.toString().lines().none { it.endsWith(" using LoggingByLog4J") })

        LoggingManager.use(LoggingByLog4J, LogLevel.Debug)
        assertEquals("${LoggingManager::class.java.name}.use", locationOf("using LoggingByLog4J"))
    }

}
