/*
 * Copyright (C) 2025 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package net.akehurst.kotlinx.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

fun <T> asyncLazy(initializer: suspend () -> T): ReadOnlyProperty<Any?, AsyncLazy<T>> = AsyncLazyDelegate(initializer)

private class AsyncLazyDelegate<T>(
     initializer: suspend () -> T
) : ReadOnlyProperty<Any?, AsyncLazy<T>> {

    // We create a single instance of the implementation to hold the state
    private val lazyInstance = AsyncLazy(initializer)

    override fun getValue(thisRef: Any?, property: KProperty<*>): AsyncLazy<T> {
        return lazyInstance
    }
}

class AsyncLazy<out T>(val initializer: suspend () -> T) {
    private object UNINITIALIZED_VALUE

    private val mutex = Mutex()
    private var _cachedValue: Any? = UNINITIALIZED_VALUE

    fun isInitialized(): Boolean = _cachedValue !== UNINITIALIZED_VALUE

    /**
     * Syntactic sugar allows calling the property like a function: `prop()`
     */
    suspend operator fun invoke(): T = await()

    suspend fun await(): T {
        // 1. Fast path: return immediately if already initialized
        val v1 = _cachedValue
        if (v1 !== UNINITIALIZED_VALUE) {
            @Suppress("UNCHECKED_CAST")
            return v1 as T
        }

        // 2. Slow path: lock and initialize
        return mutex.withLock {
            val v2 = _cachedValue
            if (v2 !== UNINITIALIZED_VALUE) {
                @Suppress("UNCHECKED_CAST")
                v2 as T
            } else {
                val result = initializer()
                _cachedValue = result
                result
            }
        }
    }
}