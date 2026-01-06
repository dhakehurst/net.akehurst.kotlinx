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

fun <IN, OUT> cachedByKey(initializer: (IN) -> OUT) = CachedValue(initializer)

class CachedValue<IN, OUT>(
   var initializer: (IN) -> OUT
) {

    private val _cache = mutableMapOf<IN, OUT>()

    fun containsKey(key: IN) = _cache.containsKey(key)

    fun getCachedValueOrNull(key: IN): OUT? = _cache[key]

    operator fun get(key: IN): OUT? = when {
        _cache.containsKey(key) -> _cache[key]
        else -> {
            val out = initializer.invoke(key)
            _cache[key] = out
            out
        }
    }

    operator fun set(key: IN, value: OUT) {
        this._cache[key] = value
    }

    fun reset(key: IN) {
        this._cache.remove(key)
    }

    fun resetAll() {
        this._cache.clear()
    }

}

