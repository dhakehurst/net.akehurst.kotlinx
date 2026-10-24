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

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import net.akehurst.kotlinx.logging.api.LogLevel
import net.akehurst.kotlinx.logging.api.LoggingManager

internal val Severity.asLogLevel: LogLevel
    get() = when (this) {
        Severity.Verbose -> LogLevel.Trace
        Severity.Debug -> LogLevel.Debug
        Severity.Info -> LogLevel.Information
        Severity.Warn -> LogLevel.Warning
        Severity.Error -> LogLevel.Error
        Severity.Assert -> LogLevel.Fatal
    }

/** Forwards Kermit messages to LoggingManager. The Kermit tag is used as the logger name, "Kermit" when empty. */
internal object KermitToLoggingManager : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        LoggingManager.logger(tag.ifEmpty { "Kermit" }).log(severity.asLogLevel, throwable) { message }
    }
}

// Kermit loggers share the global config, so this also applies to loggers created before it is called
internal fun installKermitBridge() {
    co.touchlab.kermit.Logger.setLogWriters(KermitToLoggingManager)
}
