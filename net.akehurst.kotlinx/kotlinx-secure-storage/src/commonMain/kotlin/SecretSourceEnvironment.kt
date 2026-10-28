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

/**
 * A SecretSource that reads secrets from environment variables: the secret for key K is the variable prefix+K.
 * On JVM and Node.js it reads the process environment; in a browser there are none, so every read gives null.
 */
class SecretSourceEnvironment(
    override val identity: String = "env",
    /** prepended to each key to give the variable name, e.g. "MYAPP_" */
    val prefix: String = ""
) : SecretSource {
    override suspend fun read(key: String): String? = environmentVariable(prefix + key)

    override fun toString(): String = "SecretSourceEnvironment($identity, prefix=$prefix)"
}

/** the value of the environment variable, or null if it is not set (or there is no environment) */
internal expect fun environmentVariable(name: String): String?
