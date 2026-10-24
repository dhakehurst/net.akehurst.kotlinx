/**
 * Copyright (C) 2021 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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
import net.akehurst.kotlinx.logging.api.LogThreshold
import net.akehurst.kotlinx.logging.api.Logger
import net.akehurst.kotlinx.logging.api.LoggingFramework
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.LoggerConfig
import org.apache.logging.log4j.message.Message
import org.apache.logging.log4j.message.SimpleMessage

private val LogLevel.asLog4J: org.apache.logging.log4j.Level
    get() = when (this) {
        LogLevel.Fatal -> org.apache.logging.log4j.Level.FATAL
        LogLevel.Error -> org.apache.logging.log4j.Level.ERROR
        LogLevel.Warning -> org.apache.logging.log4j.Level.WARN
        LogLevel.Information -> org.apache.logging.log4j.Level.INFO
        LogLevel.Debug -> org.apache.logging.log4j.Level.DEBUG
        LogLevel.Trace -> org.apache.logging.log4j.Level.TRACE
    }
private val LogThreshold.asLog4J: org.apache.logging.log4j.Level
    get() = when (this) {
        LogThreshold.None -> org.apache.logging.log4j.Level.OFF
        LogThreshold.All -> org.apache.logging.log4j.Level.ALL
        is LogLevel -> this.asLog4J
    }
// log4j enables a message when its level's intLevel <= the threshold's intLevel (lower is more severe).
// A custom level maps to the most verbose LogLevel it still enables, e.g. one between WARN and INFO gives Warning.
private val org.apache.logging.log4j.Level.asThreshold: LogThreshold
    get() = when {
        this.intLevel() >= org.apache.logging.log4j.Level.ALL.intLevel() -> LogThreshold.All
        else -> LogLevel.entries.lastOrNull { it.asLog4J.intLevel() <= this.intLevel() } ?: LogThreshold.None
    }

object LoggingByLog4J : LoggingFramework {
    override var rootLoggingLevel: LogThreshold
        get() = LogManager.getRootLogger().level.asThreshold
        set(value) {
            val context = LogManager.getContext(false) as LoggerContext
            val config = context.getConfiguration()
            val rootLogger: LoggerConfig = config.getRootLogger()
            rootLogger.setLevel(value.asLog4J)
            context.updateLoggers()
        }

    override fun logger(prefix: String): Logger {
        return LoggerLog4J(LogManager.getLogger(prefix))
    }
}

class LoggerLog4J(
    val log4j: org.apache.logging.log4j.Logger
) : Logger {

    companion object {
        // classes whose stack frames sit between the calling code and log4j
        private val wrapperClassNames = setOf(
            LoggerLog4J::class.java.name,
            Logger::class.java.name,
            Logger::class.java.name + "\$DefaultImpls",
        )

        // the first frame outside the wrapper classes, i.e. the code that called the logger
        private fun callerLocation(): StackTraceElement? =
            StackWalker.getInstance().walk { frames ->
                frames
                    .dropWhile { it.className !in wrapperClassNames }
                    .dropWhile { it.className in wrapperClassNames }
                    .findFirst()
                    .map { it.toStackTraceElement() }
                    .orElse(null)
            }
    }

    override fun log(level: LogLevel, t: Throwable?, lazyMessage: () -> String) {
        val lvl = level.asLog4J
        if (log4j.isEnabled(lvl)) {
            // pass the caller's location explicitly, otherwise log4j reports this class as the source
            log4j.atLevel(lvl)
                .withLocation(callerLocation())
                .withThrowable(t)
                .log(SimpleMessage(lazyMessage()) as Message)
        }
    }

}