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

package net.akehurst.kotlinx.collections

import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

fun <IN, OUT> cachedByKeySuspend(initializer: suspend (IN) -> OUT) = CachedValueSuspend(initializer)

/**
 * Caches the result of a suspending [initializer] per key.
 *
 * The initializer runs at most once at a time per key: a [get] for a key that is already being computed
 * waits for that computation instead of starting another. If the initializer fails, every waiting caller
 * gets the same exception and nothing is cached. A [reset] or [set] of the key while it is being computed
 * stops that result from being cached (callers already waiting still receive it).
 *
 * Not thread-safe: use from one thread (e.g. a single-threaded dispatcher).
 */
class CachedValueSuspend<IN, OUT>(
    var initializer: suspend (IN) -> OUT
) {

    /** callers waiting for one running computation */
    private class InFlight<OUT> {
        val waiters = mutableListOf<Continuation<OUT>>()
    }

    private val _cache = mutableMapOf<IN, OUT>()
    private val _inFlight = mutableMapOf<IN, InFlight<OUT>>()

    fun containsKey(key: IN) = _cache.containsKey(key)

    fun getCachedValueOrNull(key: IN): OUT? = _cache[key]

    suspend operator fun get(key: IN): OUT? {
        if (_cache.containsKey(key)) return _cache[key]
        val running = _inFlight[key]
        return if (null != running) {
            suspendCoroutine { cont -> running.waiters.add(cont) }
        } else {
            compute(key)
        }
    }

    private suspend fun compute(key: IN): OUT {
        val flight = InFlight<OUT>()
        _inFlight[key] = flight
        val result = runCatching { initializer.invoke(key) }
        // only cache if this is still the current computation for the key (not reset or set meanwhile)
        if (_inFlight[key] === flight) {
            _inFlight.remove(key)
            result.onSuccess { _cache[key] = it }
        }
        flight.waiters.forEach { it.resumeWith(result) }
        return result.getOrThrow()
    }

    operator fun set(key: IN, value: OUT) {
        this._inFlight.remove(key)
        this._cache[key] = value
    }

    fun reset(key: IN) {
        this._inFlight.remove(key)
        this._cache.remove(key)
    }

    fun resetAll() {
        this._inFlight.clear()
        this._cache.clear()
    }

}
