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

package net.akehurst.kotlinx.secure.storage

import com.sun.jna.Platform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class test_SecretSourceWindowsCredentialManager {

    @Test
    fun read() = runTest {
        val sut = SecretSourceWindowsCredentialManager()
        if (Platform.isWindows()) {
            assertNull(sut.read("net.akehurst.kotlinx.test.not-set-7f3a"))
        } else {
            assertFailsWith<IllegalStateException> { sut.read("x") }
        }
    }
}
