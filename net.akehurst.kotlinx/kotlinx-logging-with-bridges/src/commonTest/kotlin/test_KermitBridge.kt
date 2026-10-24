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

import co.touchlab.kermit.Logger
import net.akehurst.kotlinx.logging.api.LogLevel
import net.akehurst.kotlinx.logging.api.LoggingManager
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class test_KermitBridge {

    private val framework = CapturingLoggingFramework()

    @BeforeTest
    fun before() {
        LoggingBridges.install()
        LoggingManager.use(framework)
        framework.records.clear() // drop the "using ..." message logged by use()
    }

    @Test
    fun severities_map_to_levels() {
        val kermit = Logger.withTag("kermit-test")

        kermit.v { "verbose" }
        kermit.d { "debug" }
        kermit.i { "info" }
        kermit.w { "warn" }
        kermit.e { "error" }
        kermit.a { "assert" }

        assertEquals(
            listOf(
                CapturingLoggingFramework.Record("kermit-test", LogLevel.Trace, null, "verbose"),
                CapturingLoggingFramework.Record("kermit-test", LogLevel.Debug, null, "debug"),
                CapturingLoggingFramework.Record("kermit-test", LogLevel.Information, null, "info"),
                CapturingLoggingFramework.Record("kermit-test", LogLevel.Warning, null, "warn"),
                CapturingLoggingFramework.Record("kermit-test", LogLevel.Error, null, "error"),
                CapturingLoggingFramework.Record("kermit-test", LogLevel.Fatal, null, "assert"),
            ),
            framework.records
        )
    }

    @Test
    fun throwable_is_passed_on() {
        val ex = IllegalStateException("bad")

        Logger.withTag("kermit-test").e(ex) { "with-throwable" }

        assertSame(ex, framework.records.single().throwable)
    }

    @Test
    fun root_level_filters_kermit_messages() {
        framework.rootLoggingLevel = LogLevel.Warning

        Logger.withTag("kermit-test").i { "info" }
        Logger.withTag("kermit-test").w { "warn" }

        assertEquals(listOf("warn"), framework.records.map { it.message })
    }

}
