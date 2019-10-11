/**
 * Copyright (C) 2019 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

package net.akehurst.kotlinx.collections

inline fun <K, V> mutableMapNonNullOf() = MutableMapNonNullDefault(mutableMapOf<K,V>())

interface MapNonNull<K, out V> : Map<K, V> {
    override operator fun get(key: K): V
}

interface MutableMapNonNull<K, V> : MapNonNull<K, V>, MutableMap<K,V> {
    override operator fun get(key: K): V
}

class MutableMapNonNullDefault<K, V>(private val map:MutableMap<K,V>) : MutableMapNonNull<K,V>, MutableMap<K,V> by map {
    override fun get(key: K): V = this.map[key]!!
}