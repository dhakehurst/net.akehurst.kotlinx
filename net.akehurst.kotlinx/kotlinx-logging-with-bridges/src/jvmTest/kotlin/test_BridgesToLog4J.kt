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
package net.akehurst.kotlinx.logging.bridges

import io.github.oshai.kotlinlogging.KotlinLogging
import net.akehurst.kotlinx.logging.api.LogLevel
import net.akehurst.kotlinx.logging.api.LoggingManager
import net.akehurst.kotlinx.logging.common.LoggingByLog4J
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.WriterAppender
import org.apache.logging.log4j.core.layout.PatternLayout
import java.io.StringWriter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class test_BridgesToLog4J {

    private val appenderName = "test_BridgesToLog4J"
    private val output = StringWriter()

    @BeforeTest
    fun before() {
        val context = LogManager.getContext(false) as LoggerContext
        val appender = WriterAppender.newBuilder()
            .setName(appenderName)
            .setTarget(output)
            .setLayout(PatternLayout.newBuilder().withPattern("%level %logger %C.%M %m%n").build())
            .build()
        appender.start()
        context.configuration.rootLogger.addAppender(appender, null, null)
        context.updateLoggers()
        LoggingBridges.install()
        LoggingManager.use(LoggingByLog4J, LogLevel.Trace)
    }

    @AfterTest
    fun after() {
        val context = LogManager.getContext(false) as LoggerContext
        context.configuration.rootLogger.removeAppender(appenderName)
        context.updateLoggers()
    }

    @Test
    fun kermit_reaches_log4j_with_the_calling_code_as_location() {
        co.touchlab.kermit.Logger.withTag("kermit-test").i { "kermit-info" }

        val expected = "INFO kermit-test ${test_BridgesToLog4J::class.java.name}.kermit_reaches_log4j_with_the_calling_code_as_location kermit-info"
        assertTrue(output.toString().lines().contains(expected), output.toString())
    }

    @Test
    fun kotlin_logging_reaches_log4j_via_slf4j() {
        KotlinLogging.logger("kl-test").warn { "kl-warn" }

        val expected = "WARN kl-test ${test_BridgesToLog4J::class.java.name}.kotlin_logging_reaches_log4j_via_slf4j kl-warn"
        assertTrue(output.toString().lines().contains(expected), output.toString())
    }

}
