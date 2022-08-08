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

import kotlin.js.JsName

// provide a JS compatible Map object that has usable JSNames for the functions

inline fun <K, V> mutableJSMapOf(): MutableJSMap<K, V> = MutableJSMapDefault(mutableMapOf<K, V>())


fun <K, V> MutableMap<K,V>.jsPut(key:K, value:V) = put(key,value)

interface MutableJSMap<K, V> {
    val size: Int
    val entries: MutableSet<MutableMap.MutableEntry<K, V>>
    val keys: MutableSet<K>
    val values: MutableCollection<V>

    @JsName("clear")
    fun clear();

    @JsName("isEmpty")
    fun isEmpty(): Boolean;

    @JsName("get")
    operator fun get(key: K): V?

    @JsName("put")
    fun put(key: K, value: V): V?

    @JsName("set")
    operator fun set(key: K, value: V)

    @JsName("remove")
    fun remove(key: K): V?

    @JsName("getOrPut")
    fun getOrPut(key: K, defaultValue: () -> V): V
}

class MutableJSMapDefault<K, V>(val delegate: MutableMap<K, V>) : MutableJSMap<K, V>, MutableMap<K, V> by delegate {
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> = delegate.entries
    override val keys: MutableSet<K> = delegate.keys
    override val size: Int = delegate.size
    override val values: MutableCollection<V> = delegate.values

    override fun clear() = delegate.clear()
    override fun isEmpty(): Boolean = delegate.isEmpty()
    override operator fun get(key: K): V? = delegate.get(key)
    override fun put(key: K, value: V): V? = delegate.put(key, value)
    override operator fun set(key: K, value: V) = delegate.set(key,value)
    override fun remove(key: K): V? = delegate.remove(key)
    override fun getOrPut(key: K, defaultValue: () -> V): V  = delegate.getOrPut(key, defaultValue)
}


inline fun <K, V> mutableMapNonNullOf() = MutableMapNonNullDefault(mutableMapOf<K, V>())

interface MapNonNull<K, out V> : Map<K, V> {
    override operator fun get(key: K): V
}

interface MutableMapNonNull<K, V> : MapNonNull<K, V>, MutableMap<K, V> {
    override operator fun get(key: K): V
}

class MutableMapNonNullDefault<K, V>(private val map: MutableMap<K, V>) : MutableMapNonNull<K, V>, MutableMap<K, V> by map {
    override fun get(key: K): V = this.map[key]!!
}


class Stack<T> {

    private val _elements = mutableListOf<T>()

    val elements: List<T> = this._elements

    fun clear() {
        this._elements.clear()
    }

    fun push(element: T) {
        _elements.add(element)
    }

    fun pop(): T {
        val v = _elements.last()
        _elements.removeAt(_elements.size - 1)
        return v
    }

    fun peek(): T = _elements.last()

}

fun <T> Set<T>.transitveClosure(function: (T) -> Set<T>): Set<T> {
    var result: MutableSet<T> = this.toMutableSet()
    var newThings: MutableSet<T> = this.toMutableSet()
    var newStuff = true
    while (newStuff) {
        val temp = newThings.toSet()
        newThings.clear()
        for (nt: T in temp) {
            val s: Set<T> = function.invoke(nt)
            newThings.addAll(s)
        }
        newThings.removeAll(result)
        newStuff = result.addAll(newThings)
    }
    return result
}
