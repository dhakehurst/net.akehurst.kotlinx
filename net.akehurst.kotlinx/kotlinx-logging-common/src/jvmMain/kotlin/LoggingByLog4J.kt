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
import net.akehurst.kotlinx.logging.api.Logger
import net.akehurst.kotlinx.logging.api.LoggingFramework
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.LoggerConfig

private val LogLevel.asLog4J: org.apache.logging.log4j.Level
    get() = when (this) {
        LogLevel.None -> org.apache.logging.log4j.Level.OFF
        LogLevel.Fatal -> org.apache.logging.log4j.Level.FATAL
        LogLevel.Error -> org.apache.logging.log4j.Level.ERROR
        LogLevel.Warning -> org.apache.logging.log4j.Level.WARN
        LogLevel.Information -> org.apache.logging.log4j.Level.INFO
        LogLevel.Debug -> org.apache.logging.log4j.Level.DEBUG
        LogLevel.Trace -> org.apache.logging.log4j.Level.TRACE
        LogLevel.All -> org.apache.logging.log4j.Level.ALL
    }
private val org.apache.logging.log4j.Level.asLevel: LogLevel
    get() = when (this) {
        org.apache.logging.log4j.Level.OFF -> LogLevel.None
        org.apache.logging.log4j.Level.FATAL -> LogLevel.Fatal
        org.apache.logging.log4j.Level.ERROR -> LogLevel.Error
        org.apache.logging.log4j.Level.WARN -> LogLevel.Warning
        org.apache.logging.log4j.Level.INFO -> LogLevel.Information
        org.apache.logging.log4j.Level.DEBUG -> LogLevel.Debug
        org.apache.logging.log4j.Level.TRACE -> LogLevel.Trace
        else -> LogLevel.All //TODO: this is wrong if there are customer logging levels!
    }

object LoggingByLog4J : LoggingFramework {
    override var rootLoggingLevel
        get() = LogManager.getRootLogger().level.asLevel
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

    override fun log(level: LogLevel, t: Throwable?, lazyMessage: () -> String) {
        log4j.log(level.asLog4J, lazyMessage, t)
    }


}