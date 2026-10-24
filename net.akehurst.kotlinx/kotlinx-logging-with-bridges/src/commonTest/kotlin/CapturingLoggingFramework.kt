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
import net.akehurst.kotlinx.logging.api.LogThreshold
import net.akehurst.kotlinx.logging.api.Logger
import net.akehurst.kotlinx.logging.api.LoggingFramework

/** A logging framework that records what it is asked to log, for tests. */
class CapturingLoggingFramework : LoggingFramework {

    data class Record(val name: String, val level: LogLevel, val throwable: Throwable?, val message: String)

    override var rootLoggingLevel: LogThreshold = LogThreshold.All

    val records = mutableListOf<Record>()

    override fun logger(prefix: String): Logger = object : Logger {
        override fun log(level: LogLevel, t: Throwable?, lazyMessage: () -> String) {
            if (rootLoggingLevel.allows(level)) records.add(Record(prefix, level, t, lazyMessage()))
        }
    }
}

// true when kotlin-logging is expected to be routed to LoggingManager (not on the JVM, where it goes via SLF4J)
internal expect val kotlinLoggingIsBridged: Boolean
