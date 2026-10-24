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

import net.akehurst.kotlinx.logging.api.LogLevel
import net.akehurst.kotlinx.logging.api.LoggingManager
import net.akehurst.kotlinx.logging.common.LoggingByLog4J
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.WriterAppender
import org.apache.logging.log4j.core.layout.PatternLayout
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertTrue

class test_JulBridge {

    @Test
    fun jul_messages_go_to_log4j() {
        val output = StringWriter()
        val context = LogManager.getContext(false) as LoggerContext
        val appender = WriterAppender.newBuilder()
            .setName("test_JulBridge")
            .setTarget(output)
            .setLayout(PatternLayout.newBuilder().withPattern("%level %logger %m%n").build())
            .build()
        appender.start()
        context.configuration.rootLogger.addAppender(appender, null, null)
        context.updateLoggers()
        try {
            LoggingManager.use(LoggingByLog4J, LogLevel.Debug)

            java.util.logging.Logger.getLogger("jultest").warning("from-jul-warning")
            java.util.logging.Logger.getLogger("jultest").fine("from-jul-fine")

            assertTrue(output.toString().contains("WARN jultest from-jul-warning"), output.toString())
            // JUL FINE maps to log4j DEBUG, which is only output because JUL levels follow log4j's
            assertTrue(output.toString().contains("DEBUG jultest from-jul-fine"), output.toString())
        } finally {
            context.configuration.rootLogger.removeAppender("test_JulBridge")
            context.updateLoggers()
        }
    }

}
