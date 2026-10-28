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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A SecretSource that asks the user, through the application's own UI, and remembers each answer in memory for the life of this source.
 * Works on every platform, including browser apps. In a browser, make the prompt a login form (a username and a password
 * field, on the app's URL) so that a password manager such as KeePassXC-Browser can fill it in.
 */
class SecretSourcePrompt(
    override val identity: String = "prompt",
    /** asks the user for the secret for "key"; null if they cancel */
    private val prompt: suspend (key: String) -> String?
) : SecretSource {
    private val _mutex = Mutex()
    private val _answers = mutableMapOf<String, String>()

    override suspend fun read(key: String): String? = _mutex.withLock {
        _answers[key] ?: prompt(key)?.also { _answers[key] = it }
    }

    /** forgets the remembered answers, so the user is asked again */
    fun forget() {
        _answers.clear()
    }

    override fun toString(): String = "SecretSourcePrompt($identity)"
}
