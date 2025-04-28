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

enum class LogLevel { None, Fatal, Error, Warning, Information, Debug, Trace, All }

typealias LogFunction = (level: LogLevel, prefix: String, t: Throwable?, lazyMessage: () -> String) -> Unit

interface Logger {
    var outputLevel: LogLevel

    fun log(level: LogLevel, t: Throwable? = null, lazyMessage: () -> String)

    fun logFatal(t: Throwable? = null, lazyMessage: () -> String) = log(LogLevel.Fatal, t, lazyMessage)
    fun logError(t: Throwable? = null, lazyMessage: () -> String) = log(LogLevel.Error, t, lazyMessage)
    fun logWarning(t: Throwable? = null, lazyMessage: () -> String) = log(LogLevel.Warning, t, lazyMessage)
    fun logInformation(t: Throwable? = null, lazyMessage: () -> String) = log(LogLevel.Information, t, lazyMessage)
    fun logDebug(t: Throwable? = null, lazyMessage: () -> String) = log(LogLevel.Debug, t, lazyMessage)
    fun logTrace(t: Throwable? = null, lazyMessage: () -> String) = log(LogLevel.Trace, t, lazyMessage)
}