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

package net.akehurst.kotlinx.logging.api

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class test_LogThreshold {

    @Test
    fun None_allows_no_level() {
        LogLevel.entries.forEach { assertFalse(LogThreshold.None.allows(it), "$it") }
    }

    @Test
    fun All_allows_every_level() {
        LogLevel.entries.forEach { assertTrue(LogThreshold.All.allows(it), "$it") }
    }

    @Test
    fun level_allows_itself_and_more_severe_only() {
        val threshold = LogLevel.Information
        assertTrue(threshold.allows(LogLevel.Fatal))
        assertTrue(threshold.allows(LogLevel.Error))
        assertTrue(threshold.allows(LogLevel.Warning))
        assertTrue(threshold.allows(LogLevel.Information))
        assertFalse(threshold.allows(LogLevel.Debug))
        assertFalse(threshold.allows(LogLevel.Trace))
    }

}
