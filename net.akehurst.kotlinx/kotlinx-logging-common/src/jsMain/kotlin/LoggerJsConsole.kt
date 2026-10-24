/**
 * Copyright (C) 2024 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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
import net.akehurst.kotlinx.logging.api.LoggingManager
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

class LoggerJsConsole(
    val prefix: String
) : Logger {

    val outputLevel: LogThreshold get() = LoggingManager.rootLoggingLevel

    override fun log(level: LogLevel, t: Throwable?, lazyMessage: () -> String) {
        when {
            outputLevel.allows(level) -> logAll(level, lazyMessage(), t)
            else -> Unit
        }
    }

    private fun logAll(lvl: LogLevel, msg: String?, t: Throwable?) {
        val func = when (lvl) {
            LogLevel.Fatal -> this::consoleFatal
            LogLevel.Error -> this::consoleError
            LogLevel.Warning -> this::consoleWarn
            LogLevel.Information -> this::consoleInfo
            LogLevel.Debug -> this::consoleDebug
            LogLevel.Trace -> this::consoleTrace
        }
        func("$prefix - $msg", t)
    }

    // the throwable is passed to the console with the message (it is a JS Error), so the browser shows its stack with the message
    private fun consoleArgs(msg: String, t: Throwable?): Array<Any?> = if (null == t) arrayOf(msg) else arrayOf(msg, t)

    private fun consoleFatal(msg: String?, t: Throwable?) = console.error(*consoleArgs(duration("Fatal", "$msg"), t))
    private fun consoleError(msg: String?, t: Throwable?) = console.error(*consoleArgs(duration("Error", "$msg"), t))
    private fun consoleWarn(msg: String?, t: Throwable?) = console.warn(*consoleArgs(duration("Warn", "$msg"), t))
    private fun consoleInfo(msg: String?, t: Throwable?) = console.info(*consoleArgs(duration("Info", "$msg"), t))
    private fun consoleDebug(msg: String?, t: Throwable?) = console.asDynamic().debug.apply(console, consoleArgs(duration("Debug", "$msg"), t))
    private fun consoleTrace(msg: String?, t: Throwable?) = console.asDynamic().debug.apply(console, consoleArgs(duration("Trace", "$msg"), t))

    private var lastTimeMark: TimeSource.Monotonic.ValueTimeMark? = null
    private fun duration(lev: String, msg: String): String {
        return if (msg.startsWith("Send") || msg.startsWith("Receiv")) {
            val prev = lastTimeMark
            val now = TimeSource.Monotonic.markNow()
            lastTimeMark = now
            val durStr = prev?.let {
                val dur = now - it
                dur.toString(DurationUnit.MILLISECONDS)
            } ?: "0"
            "$lev ($durStr): $msg"
        } else {
            "$lev: $msg"
        }
    }
}

object LoggingByBrowserConsoleJs: LoggingFramework {
    override var rootLoggingLevel: LogThreshold = LogLevel.Information

    override fun logger(prefix: String): Logger = LoggerJsConsole(prefix)

}