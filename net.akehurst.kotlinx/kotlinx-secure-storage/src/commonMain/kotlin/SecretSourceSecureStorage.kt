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
 * A SecretSource that reads secrets from a SecureStorage (e.g. a SecureStorageDatastore file, optionally encrypted).
 * The secret for key K is the value stored under K; a value that is not a String is given by its toString().
 */
class SecretSourceSecureStorage(
    override val identity: String,
    val storage: SecureStorage
) : SecretSource {
    override suspend fun read(key: String): String? = storage.read(key)?.let { it as? String ?: it.toString() }

    override fun toString(): String = "SecretSourceSecureStorage($identity)"
}
