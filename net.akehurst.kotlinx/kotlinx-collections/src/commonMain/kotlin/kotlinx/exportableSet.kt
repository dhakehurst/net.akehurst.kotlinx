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
/*
val <E> Set<E>.exportable: SetExportable<E> get() = SetExportableDefault(this)
val <E> SetExportable<E>.nonExportable: Set<E> get() = (this as SetExportableDefault).delegate
val <E> MutableSet<E>.exportableM: MutableSetExportable<E> get() = MutableSetExportableDefault(this)
val <E> MutableSetExportable<E>.nonExportable: MutableSet<E> get() = (this as MutableSetExportableDefault).delegate

fun <E> exportableSetOf(vararg elements: E): SetExportable<E> = SetExportableDefault(setOf<E>(*elements))
fun <E> mutableExportableSetOf(vararg elements: E): MutableSetExportable<E> = MutableSetExportableDefault(mutableSetOf<E>(*elements))

interface SetExportable<out E> : CollectionExportable<E> {
    override val size: Int
    override fun isEmpty(): Boolean
    override fun contains(element: @UnsafeVariance E): Boolean
    override fun iterator(): IteratorExportable<E>
    override fun containsAll(elements: CollectionExportable<@UnsafeVariance E>): Boolean
}

interface MutableSetExportable<E> : SetExportable<E>, MutableCollectionExportable<E> {
    override fun iterator(): MutableIteratorExportable<E>
    override fun add(element: E): Boolean
    override fun remove(element: E): Boolean
    override fun addAll(elements: CollectionExportable<E>): Boolean
    override fun removeAll(elements: CollectionExportable<E>): Boolean
    override fun retainAll(elements: CollectionExportable<E>): Boolean
    override fun clear(): Unit
}

class SetExportableDefault<E>(val delegate: Set<E>) : SetExportable<E> {
    override val size: Int get() = delegate.size
    override fun isEmpty(): Boolean = delegate.isEmpty()
    override fun iterator(): IteratorExportable<E> = delegate.iterator().exportable
    override fun containsAll(elements: CollectionExportable<@UnsafeVariance E>): Boolean = delegate.containsAll(elements.nonExportable)
    override fun contains(element: E): Boolean = delegate.contains(element)
}

class MutableSetExportableDefault<E>(val delegate: MutableSet<E>) : MutableSetExportable<E> {
    override val size: Int get() = delegate.size
    override fun iterator(): MutableIteratorExportable<E> = delegate.iterator().exportableM
    override fun clear() = delegate.clear()
    override fun retainAll(elements: CollectionExportable<E>): Boolean = delegate.retainAll(elements.nonExportable)
    override fun removeAll(elements: CollectionExportable<E>): Boolean = delegate.removeAll(elements.nonExportable)
    override fun addAll(elements: CollectionExportable<E>): Boolean = delegate.addAll(elements.nonExportable)
    override fun remove(element: E): Boolean = delegate.remove(element)
    override fun add(element: E): Boolean = delegate.add(element)
    override fun isEmpty(): Boolean = delegate.isEmpty()
    override fun containsAll(elements: CollectionExportable<E>): Boolean = delegate.containsAll(elements.nonExportable)
    override fun contains(element: E): Boolean = delegate.contains(element)
}
*/

