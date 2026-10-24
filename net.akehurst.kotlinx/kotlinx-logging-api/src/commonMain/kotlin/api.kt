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

package net.akehurst.kotlinx.logging.api

import kotlin.concurrent.Volatile

/**
 * A root logging level: which messages are output.
 * Either a [LogLevel] (output messages at that level or more severe), [None] or [All].
 */
sealed interface LogThreshold {
    /** lower is more severe */
    val severity: Int

    /** true if a message at [level] is output under this threshold */
    fun allows(level: LogLevel): Boolean = level.severity <= this.severity

    /** output no messages */
    data object None : LogThreshold {
        override val severity: Int = 0
    }

    /** output all messages */
    data object All : LogThreshold {
        override val severity: Int = Int.MAX_VALUE
    }
}

/**
 * The level of a logged message, most severe first.
 * Each level can also be used as a [LogThreshold].
 */
enum class LogLevel(override val severity: Int) : LogThreshold {
    Fatal(1), Error(2), Warning(3), Information(4), Debug(5), Trace(6)
}

typealias LogFunction = (level: LogLevel, prefix: String, t: Throwable?, lazyMessage: () -> String) -> Unit

interface Logger {
    fun log(level: LogLevel, t: Throwable? = null, lazyMessage: () -> String)

    fun logFatal(t: Throwable? = null, lazyMessage: () -> String) = log(LogLevel.Fatal, t, lazyMessage)
    fun logError(t: Throwable? = null, lazyMessage: () -> String) = log(LogLevel.Error, t, lazyMessage)
    fun logWarning(t: Throwable? = null, lazyMessage: () -> String) = log(LogLevel.Warning, t, lazyMessage)
    fun logInformation(t: Throwable? = null, lazyMessage: () -> String) = log(LogLevel.Information, t, lazyMessage)
    fun logDebug(t: Throwable? = null, lazyMessage: () -> String) = log(LogLevel.Debug, t, lazyMessage)
    fun logTrace(t: Throwable? = null, lazyMessage: () -> String) = log(LogLevel.Trace, t, lazyMessage)


    fun logIf(predicate: () -> Boolean, level: LogLevel, t: Throwable? = null, lazyMessage: () -> String) {
        if (predicate.invoke()) {
            log(level, t, lazyMessage)
        }
    }
    fun logFatalIf(predicate: () -> Boolean, t: Throwable? = null, lazyMessage: () -> String) = logIf(predicate, LogLevel.Fatal, t, lazyMessage)
    fun logErrorIf(predicate: () -> Boolean, t: Throwable? = null, lazyMessage: () -> String) = logIf(predicate, LogLevel.Error, t, lazyMessage)
    fun logWarningIf(predicate: () -> Boolean, t: Throwable? = null, lazyMessage: () -> String) = logIf(predicate, LogLevel.Warning, t, lazyMessage)
    fun logInformationIf(predicate: () -> Boolean, t: Throwable? = null, lazyMessage: () -> String) = logIf(predicate, LogLevel.Information, t, lazyMessage)
    fun logDebugIf(predicate: () -> Boolean, t: Throwable? = null, lazyMessage: () -> String) = logIf(predicate, LogLevel.Debug, t, lazyMessage)
    fun logTraceIf(predicate: () -> Boolean, t: Throwable? = null, lazyMessage: () -> String) = logIf(predicate, LogLevel.Trace, t, lazyMessage)
}


interface LoggingFramework {
    var rootLoggingLevel: LogThreshold
    fun logger(prefix: String): Logger
}

fun logger(prefix: String): Logger = LoggingManager.logger(prefix)

object LoggingManager {

    // volatile so a framework set by use() on one thread is seen by others
    @Volatile
    private var _framework: LoggingFramework = LoggingByConsole

    var rootLoggingLevel: LogThreshold
        get() = _framework.rootLoggingLevel
        set(value) = _framework.let { it.rootLoggingLevel = value }

    /**
     * Switch to the given logging framework.
     * The root level is only changed when [rootLoggingLevel] is given,
     * otherwise the framework keeps its own level (e.g. from a log4j2 config file).
     */
    fun use(framework: LoggingFramework, rootLoggingLevel: LogThreshold? = null) {
        _framework = framework
        if (null != rootLoggingLevel) this.rootLoggingLevel = rootLoggingLevel
        framework.logger("LoggingManager").logDebug { "using ${_framework::class.simpleName}" }
    }

    fun logger(prefix: String): Logger = _framework.logger(prefix)

    init {
        _framework.logger("LoggingManager").logDebug { "using ${_framework::class.simpleName}" }
    }
}