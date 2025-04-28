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

import net.akehurst.kotlinx.logging.api.*

class LoggerCommon(
    val prefix: String,
    var bind: LogFunction
) : Logger {

    override var outputLevel = LogLevel.All

    override fun log(level: LogLevel, t: Throwable?, lazyMessage: () -> String) {
        when {
            level <= this.outputLevel ->  this.bind.invoke(level, prefix, t, lazyMessage)
            else -> Unit // do nothing
        }
    }


}

class LoggerConsole(
    val prefix: String
) : Logger {

    override var outputLevel = LogLevel.All

    override fun log(level: LogLevel, t: Throwable?, lazyMessage: () -> String) {
        when {
            level <= this.outputLevel ->  println("$level: $prefix - ${lazyMessage()}")
            else -> Unit // do nothing
        }
    }

}