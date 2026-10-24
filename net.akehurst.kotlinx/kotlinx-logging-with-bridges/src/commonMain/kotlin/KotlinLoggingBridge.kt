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

import io.github.oshai.kotlinlogging.Appender
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KLoggerFactory
import io.github.oshai.kotlinlogging.KLoggingEvent
import io.github.oshai.kotlinlogging.KLoggingEventBuilder
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import io.github.oshai.kotlinlogging.Marker
import net.akehurst.kotlinx.logging.api.LogLevel
import net.akehurst.kotlinx.logging.api.LoggingManager

internal val Level.asLogLevel: LogLevel?
    get() = when (this) {
        Level.TRACE -> LogLevel.Trace
        Level.DEBUG -> LogLevel.Debug
        Level.INFO -> LogLevel.Information
        Level.WARN -> LogLevel.Warning
        Level.ERROR -> LogLevel.Error
        Level.OFF -> null
    }

/** A kotlin-logging logger that forwards to LoggingManager, using the same logger name. */
internal class KotlinLoggingToLoggingManager(override val name: String) : KLogger {

    override fun isLoggingEnabledFor(level: Level, marker: Marker?): Boolean =
        level.asLogLevel?.let { LoggingManager.rootLoggingLevel.allows(it) } ?: false

    override fun at(level: Level, marker: Marker?, block: KLoggingEventBuilder.() -> Unit) {
        if (isLoggingEnabledFor(level, marker)) {
            val event = KLoggingEventBuilder().apply(block)
            LoggingManager.logger(name).log(level.asLogLevel!!, event.cause) { event.message ?: "" }
        }
    }
}

internal object KotlinLoggingToLoggingManagerFactory : KLoggerFactory {
    override fun logger(name: String): KLogger = KotlinLoggingToLoggingManager(name)
}

// receives messages from kotlin-logging loggers created before the bridge was installed
internal object KotlinLoggingToLoggingManagerAppender : Appender {
    override fun log(loggingEvent: KLoggingEvent) {
        val level = loggingEvent.level.asLogLevel ?: return
        LoggingManager.logger(loggingEvent.loggerName).log(level, loggingEvent.cause) { loggingEvent.message ?: "" }
    }
}

internal fun installKotlinLoggingToLoggingManager() {
    // kotlin-logging reads the factory only when a logger is created, so loggers that already exist
    // keep using its 'direct' output: send that to LoggingManager too, and let LoggingManager filter by level.
    // Set before the factory, as kotlin-logging warns about 'direct' changes when another factory is active.
    KotlinLoggingConfiguration.direct.appender = KotlinLoggingToLoggingManagerAppender
    KotlinLoggingConfiguration.direct.logLevel = Level.TRACE
    KotlinLoggingConfiguration.loggerFactory = KotlinLoggingToLoggingManagerFactory
}
