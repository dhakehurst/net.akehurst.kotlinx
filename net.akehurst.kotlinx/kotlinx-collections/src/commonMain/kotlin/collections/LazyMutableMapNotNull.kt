/*
 * Copyright (C) 2023 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

import kotlin.reflect.KProperty

fun <K, V> lazyMapNotNull(accessor: (K) -> V) = LazyMapNotNull(accessor)
fun <K, V> lazyMutableMapNotNull(accessor: (K) -> V) = LazyMutableMapNotNull(accessor)

class LazyMapNotNull<K, V>(val accessor: (K) -> V) : MapNotNull<K, V> {

    val map = hashMapOf<K, V>()

    override operator fun get(key: K): V {
        return if (map.containsKey(key)) {
            map.get(key) ?: throw Exception("This map should never contain nulls")
        } else {
            val v = accessor.invoke(key)
            map[key] = v
            v
        }
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = map.entries
    override val keys: MutableSet<K> get() = map.keys
    override val size: Int get() = map.size
    override val values: MutableCollection<V> get() = map.values

    override fun containsKey(key: K): Boolean = map.containsKey(key)
    override fun containsValue(value: V): Boolean = map.containsValue(value)

    override fun isEmpty(): Boolean = map.isEmpty()

    // enable property delegation
    operator fun getValue(thisRef: Any?, property: KProperty<*>): LazyMapNotNull<K, V> = this
}

class LazyMutableMapNotNull<K, V>(val accessor: (K) -> V) : MutableMapNotNull<K, V> {

    val map = hashMapOf<K, V>()

    override operator fun get(key: K): V {
        return if (map.containsKey(key)) {
            map.get(key) ?: throw Exception("This map should never contain nulls")
        } else {
            val v = accessor.invoke(key)
            map[key] = v
            v
        }
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = map.entries
    override val keys: MutableSet<K> get() = map.keys
    override val size: Int get() = map.size
    override val values: MutableCollection<V> get() = map.values

    override fun containsKey(key: K): Boolean = map.containsKey(key)
    override fun containsValue(value: V): Boolean = map.containsValue(value)
    override fun isEmpty(): Boolean = map.isEmpty()

    override fun clear() {
        this.map.clear()
    }

    override fun put(key: K, value: V): V? = this.map.put(key, value)
    override fun putAll(from: Map<out K, V>) {
        this.map.putAll(from)
    }

    override fun remove(key: K): V? = this.map.remove(key)

    // enable property delegation
    operator fun getValue(thisRef: Any?, property: KProperty<*>): MutableMapNotNull<K, V> = this
}
