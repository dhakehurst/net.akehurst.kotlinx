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

/**
 * Routes messages logged with other Kotlin logging libraries to [net.akehurst.kotlinx.logging.api.LoggingManager],
 * so they are output by whichever framework is in use.
 *
 * - Kermit: on all platforms.
 * - kotlin-logging: on JS, Wasm and native. On the JVM it already logs via SLF4J, which this module routes to log4j.
 *
 * Call [install] once at startup, typically next to `LoggingManager.use(...)`.
 */
object LoggingBridges {

    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        installKermitBridge()
        installKotlinLoggingBridge()
        registerBridgeClassesWithFramework()
    }

}

// on the JVM kotlin-logging is left on its SLF4J route; elsewhere it is redirected to LoggingManager
internal expect fun installKotlinLoggingBridge()

// lets the JVM log4j logger skip the bridge classes when it works out the calling code
internal expect fun registerBridgeClassesWithFramework()
