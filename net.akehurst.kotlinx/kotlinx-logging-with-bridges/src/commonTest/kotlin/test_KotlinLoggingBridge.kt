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

import io.github.oshai.kotlinlogging.KLoggingEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.Level
import net.akehurst.kotlinx.logging.api.LogLevel
import net.akehurst.kotlinx.logging.api.LoggingManager
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class test_KotlinLoggingBridge {

    private val framework = CapturingLoggingFramework()

    @BeforeTest
    fun before() {
        LoggingBridges.install()
        LoggingManager.use(framework)
    }

    @Test
    fun new_logger_is_routed_to_LoggingManager() {
        if (!kotlinLoggingIsBridged) return
        val ex = IllegalStateException("bad")
        val logger = KotlinLogging.logger("kl-test")

        logger.trace { "trace" }
        logger.debug { "debug" }
        logger.info { "info" }
        logger.warn { "warn" }
        logger.error(ex) { "error" }

        assertEquals(
            listOf(
                CapturingLoggingFramework.Record("kl-test", LogLevel.Trace, null, "trace"),
                CapturingLoggingFramework.Record("kl-test", LogLevel.Debug, null, "debug"),
                CapturingLoggingFramework.Record("kl-test", LogLevel.Information, null, "info"),
                CapturingLoggingFramework.Record("kl-test", LogLevel.Warning, null, "warn"),
                CapturingLoggingFramework.Record("kl-test", LogLevel.Error, ex, "error"),
            ),
            framework.records
        )
    }

    @Test
    fun root_level_is_checked_before_building_the_message() {
        if (!kotlinLoggingIsBridged) return
        framework.rootLoggingLevel = LogLevel.Information
        var built = false

        KotlinLogging.logger("kl-test").debug { built = true; "debug" }

        assertEquals(false, built)
        assertTrue(framework.records.isEmpty())
    }

    @Test
    fun output_of_loggers_created_before_install_is_routed_to_LoggingManager() {
        if (!kotlinLoggingIsBridged) return
        val ex = IllegalStateException("bad")

        // loggers created before install() log through kotlin-logging's 'direct' appender
        KotlinLoggingToLoggingManagerAppender.log(KLoggingEvent(Level.WARN, null, "early", "from-early-logger", ex))
        KotlinLoggingToLoggingManagerAppender.log(KLoggingEvent(Level.OFF, null, "early", "off"))

        assertEquals(
            listOf(CapturingLoggingFramework.Record("early", LogLevel.Warning, ex, "from-early-logger")),
            framework.records
        )
    }

}
