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

import net.akehurst.kotlinx.logging.common.LoggerLog4J

// kotlin-logging already logs via SLF4J on the JVM, which log4j-slf4j2-impl routes to log4j
internal actual fun installKotlinLoggingBridge() = Unit

internal actual fun registerBridgeClassesWithFramework() {
    LoggerLog4J.addWrapperClassNames(
        KermitToLoggingManager::class.java.name,
        "co.touchlab.kermit.BaseLogger",
        "co.touchlab.kermit.Logger",
        "co.touchlab.kermit.Logger\$Companion",
    )
}
