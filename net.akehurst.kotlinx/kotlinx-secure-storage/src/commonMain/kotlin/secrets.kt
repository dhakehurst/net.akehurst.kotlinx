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
 * A store that secrets are read from, e.g. an OS keychain, environment variables, a password manager, a vault,
 * or a SecureStorage file. Register implementations in a SecretSourceRegistry.
 */
interface SecretSource {
    val identity: String

    /** the secret stored under 'key', or null if there is none */
    suspend fun read(key: String): String?
}

interface SecretSourceRegistry {
    fun register(source: SecretSource)
    fun getOrNull(identity: String): SecretSource?
}

class SecretSourceRegistryDefault : SecretSourceRegistry {
    companion object {
        val GLOBAL = SecretSourceRegistryDefault()
    }

    private val _source = mutableMapOf<String, SecretSource>()

    override fun register(source: SecretSource) {
        _source[source.identity] = source
    }

    override fun getOrNull(identity: String): SecretSource? = _source[identity]
}

/** a SecretSource that holds its secrets in memory, e.g. for tests, or for secrets the user has typed in */
class SecretSourceInMemory(
    override val identity: String,
    secrets: Map<String, String> = emptyMap()
) : SecretSource {
    private val _secrets = secrets.toMutableMap()

    fun set(key: String, value: String) {
        _secrets[key] = value
    }

    fun remove(key: String) {
        _secrets.remove(key)
    }

    override suspend fun read(key: String): String? = _secrets[key]

    override fun toString(): String = "SecretSourceInMemory($identity)"
}
