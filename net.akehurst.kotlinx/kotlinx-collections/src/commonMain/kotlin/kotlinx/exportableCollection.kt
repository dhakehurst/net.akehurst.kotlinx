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

val <E> Iterator<E>.exportable: IteratorExportable<E> get() = IteratorExportableDefault(this)
val <E> IteratorExportable<E>.nonExportable: Iterator<E> get() = (this as IteratorExportableDefault<E>).delegate

val <E> MutableIterator<E>.exportableM: MutableIteratorExportable<E> get() = MutableIteratorExportableDefault(this)
val <E> MutableIteratorExportable<E>.nonExportable: MutableIterator<E> get() = (this as MutableIteratorExportableDefault<E>).delegate

val <E> Collection<E>.exportable: CollectionExportable<E> get() = CollectionExportableDefault(this)
val <E> CollectionExportable<E>.nonExportable: Collection<E> get() = (this as CollectionExportableDefault<E>).delegate

val <E> MutableCollection<E>.exportableM: MutableCollectionExportable<E> get() = MutableCollectionExportableDefault(this)
val <E> MutableCollectionExportable<E>.nonExportable: MutableCollection<E> get() = (this as MutableCollectionExportableDefault<E>).delegate

interface IteratorExportable<out T> {
    operator fun next(): T
    operator fun hasNext(): Boolean
}

interface MutableIteratorExportable<out T> : IteratorExportable<T> {
    fun remove(): Unit
}

interface IterableExportable<out T> {
    operator fun iterator(): IteratorExportable<T>
}

interface MutableIterableExportable<out T> : IterableExportable<T> {
    override fun iterator(): MutableIteratorExportable<T>
}

interface CollectionExportable<out E> : IterableExportable<E> {
    val size: Int

    operator fun contains(element: @UnsafeVariance E): Boolean

    fun isEmpty(): Boolean
    override fun iterator(): IteratorExportable<E>
    fun containsAll(elements: CollectionExportable<@UnsafeVariance E>): Boolean
}

interface MutableCollectionExportable<E> : CollectionExportable<E>, MutableIterableExportable<E> {
    override fun iterator(): MutableIteratorExportable<E>
    fun add(element: E): Boolean
    fun remove(element: E): Boolean
    fun addAll(elements: CollectionExportable<E>): Boolean
    fun removeAll(elements: CollectionExportable<E>): Boolean
    fun retainAll(elements: CollectionExportable<E>): Boolean
    fun clear(): Unit
}

class IteratorExportableDefault<E>(val delegate: Iterator<E>) : IteratorExportable<E> {
    override fun next(): E = delegate.next()
    override fun hasNext(): Boolean = delegate.hasNext()
}

class MutableIteratorExportableDefault<E>(val delegate: MutableIterator<E>) : MutableIteratorExportable<E> {
    override fun remove() = delegate.remove()
    override fun next(): E = delegate.next()
    override fun hasNext(): Boolean = delegate.hasNext()
}

class CollectionExportableDefault<E>(val delegate: Collection<E>) : CollectionExportable<E> {
    override val size: Int get() = delegate.size
    override fun isEmpty(): Boolean = delegate.isEmpty()
    override fun iterator(): IteratorExportable<E> = delegate.iterator().exportable
    override fun containsAll(elements: CollectionExportable<E>): Boolean = delegate.containsAll(elements.nonExportable)
    override fun contains(element: E): Boolean = delegate.contains(element)
}

class MutableCollectionExportableDefault<E>(val delegate: MutableCollection<E>) : MutableCollectionExportable<E> {
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