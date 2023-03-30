/**
 * Copyright (C) 2023 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

val <K, V> Map<K, V>.exportable: MapExportable<K, V> get() = MapExportableDefault(this)
val <K, V> MapExportable<K,V>.nonExportable: Map<K,V> get() = (this as MapExportableDefault).delegate
val <K, V> MutableMap<K, V>.exportableM: MutableMapExportable<K, V> get() = MutableMapExportableDefault(this)
val <K, V> MutableMapExportable<K,V>.nonExportable: MutableMap<K,V> get() = (this as MutableMapExportableDefault).delegate

fun <K, V> mapExportableOf(vararg pairs: Pair<K, V>): MapExportable<K, V> = MapExportableDefault(mapOf<K, V>(*pairs))
fun <K, V> mutableMapExportableOf(vararg pairs: Pair<K, V>): MutableMapExportable<K, V> = MutableMapExportableDefault(mutableMapOf<K, V>(*pairs))

//fun <K, V> MutableMap<K, V>.jsPut(key: K, value: V) = put(key, value)

interface MapExportable<K, out V> {
    interface Entry<out K, out V> {
        val key: K
        val value: V
    }

    val size: Int
    val keys: SetExportable<K>
    val values: CollectionExportable<V>
    val entries: SetExportable<MapExportable.Entry<K, V>>

    fun isEmpty(): Boolean
    fun containsKey(key: K): Boolean
    fun containsValue(value: @UnsafeVariance V): Boolean
    fun getOrDefault(key: K, defaultValue: @UnsafeVariance V): V

    operator fun get(key: K): V?
}

interface MutableMapExportable<K, V> : MapExportable<K, V> {

    interface MutableEntry<K, V> : MapExportable.Entry<K, V> {
        fun setValue(newValue: V): V
    }

    override val keys: MutableSetExportable<K>
    override val values: MutableCollectionExportable<V>
    override val entries: MutableSetExportable<MutableMapExportable.MutableEntry<K, V>>

    operator fun set(key: K, value: V): Unit

    fun put(key: K, value: V): V?
    fun remove(key: K): V?
    fun putAll(from: MapExportable<out K, V>): Unit
    fun clear(): Unit
}

class MapExportableDefault<K, V>(val delegate: Map<K, V>) : MapExportable<K, V> {
    class EntryDefault<out K, out V>(val delegate: Map.Entry<K, V>) : MapExportable.Entry<K, V> {
        override val key: K get() = delegate.key
        override val value: V get() = delegate.value
        override fun hashCode(): Int = key.hashCode()
        override fun equals(other: Any?): Boolean = when (other){
            !is MapExportable.Entry<*, *> -> false
            else -> this.key == other.key
        }
    }

    override val size: Int get() = delegate.size
    override val keys: SetExportable<K> get() = delegate.keys.exportable
    override val values: CollectionExportable<V> get() = delegate.values.exportable
    override val entries: SetExportable<MapExportable.Entry<K, V>> get() = delegate.entries.map { EntryDefault(it) }.toSet().exportable

    override fun isEmpty(): Boolean = delegate.isEmpty()
    override fun getOrDefault(key: K, defaultValue: V): V = delegate.get(key) ?: defaultValue
    override fun containsValue(value: V): Boolean = delegate.containsValue(value)
    override fun containsKey(key: K): Boolean = delegate.containsKey(key)

    override fun get(key: K): V? = delegate.get(key)
}

class MutableMapExportableDefault<K, V>(val delegate: MutableMap<K, V>) : MutableMapExportable<K, V> {
    class MutableEntryDefault<K, V>(val delegate: MutableMap.MutableEntry<K, V>) : MutableMapExportable.MutableEntry<K, V> {
        override val key: K get() = delegate.key
        override val value: V get() = delegate.value
        override fun setValue(newValue: V): V =delegate.setValue(newValue)
        override fun hashCode(): Int = key.hashCode()
        override fun equals(other: Any?): Boolean = when (other){
            !is MapExportable.Entry<*, *> -> false
            else -> this.key == other.key
        }
    }

    override val entries: MutableSetExportable<MutableMapExportable.MutableEntry<K, V>> = delegate.entries.map { MutableEntryDefault(it) as MutableMapExportable.MutableEntry<K, V>}.toMutableSet().exportableM
    override val keys: MutableSetExportable<K> = delegate.keys.exportableM
    override val size: Int = delegate.size
    override val values: MutableCollectionExportable<V> = delegate.values.exportableM

    override fun clear() = delegate.clear()
    override fun isEmpty(): Boolean = delegate.isEmpty()
    override operator fun get(key: K): V? = delegate.get(key)
    override fun put(key: K, value: V): V? = delegate.put(key, value)
    override operator fun set(key: K, value: V) = delegate.set(key, value)
    override fun remove(key: K): V? = delegate.remove(key)
    //override fun getOrPut(key: K, defaultValue: () -> V): V = delegate.getOrPut(key, defaultValue)
    override fun putAll(from: MapExportable<out K, V>) = delegate.putAll(from.nonExportable)
    override fun containsKey(key: K): Boolean =delegate.containsKey(key)
    override fun containsValue(value: V): Boolean = delegate.containsValue(value)
    override fun getOrDefault(key: K, defaultValue: V): V = delegate.get(key) ?: defaultValue
}


