/*
 * Copyright (C) 2026 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

import kotlin.reflect.KClass

interface ReferenceStore<R : Any> {
    operator fun <T : Any> get(clazz: KClass<T>, reference: R): T?

    /**
     * Stores [value] under type [clazz] and key [reference]. Setting `null` removes the entry.
     */
    operator fun <T : Any> set(clazz: KClass<T>, reference: R, value: T?)

    /**
     * Removes the object stored under type [clazz] and key [reference], if there is one.
     * The same as `set(clazz, reference, null)`.
     */
    fun <T : Any> remove(clazz: KClass<T>, reference: R) = set(clazz, reference, null)
}

interface HierarchicalReferenceStore<R : Any> : ReferenceStore<R> {
    val identity: R
    val parentReferenceStore: HierarchicalReferenceStore<R>?
    val rootReferenceStore: HierarchicalReferenceStore<R>

    /**
     * register a child with this store so qualified lookups from the root can traverse to children
     */
    fun registerChild(child: HierarchicalReferenceStore<R>)
    fun <T : Any> getOwnerForReferenceType(clazz: KClass<T>):ReferenceStore<R>?

    /**
     * Records [store] as the one store that holds objects of type [clazz].
     * Throws IllegalStateException if a different store already holds that type.
     */
    fun <T : Any> setOwnerForReferenceType(clazz: KClass<T>, store: ReferenceStore<R>)

    /**
     * Finds the object for [reference] anywhere in the tree of stores.
     * Looks first in the store that holds type [clazz]. If it isn't there, accepts an object stored under
     * any other type, with the same key, that is an instance of [clazz] (i.e. an object of a subtype).
     * Throws IllegalStateException if more than one such object matches.
     */
    fun <T : Any> findInTree(clazz: KClass<T>, reference: R): T?

    //fun <T : Any> getQualifiedReference(clazz: KClass<T>, qualifiedReference: List<R>): T?
}

class ReferenceStoreByHashMap<R : Any> : ReferenceStore<R> {
    private val _map = HashMap<KClass<*>, HashMap<R, Any>>()

    override fun <T : Any> get(clazz: KClass<T>, reference: R): T? = _map[clazz]?.get(reference) as? T

    override fun <T : Any> set(clazz: KClass<T>, reference: R, value: T?) {
        when (value) {
            null -> _map[clazz]?.remove(reference)
            else -> {
                val clsMap = _map[clazz] ?: let { HashMap<R, Any>().also { _map[clazz] = it } }
                clsMap[reference] = value
            }
        }
    }
}

object ReferenceStoreExt {
    /**
     * returns the object fo the given reference if found.
     * Also, if the reference is a [ResolvableReference] and the object is found, sets it as the resolved value.
     *
     * If nothing is found, the reference's resolved value is left as it is, unless [clearIfNotFound] is true:
     * then it is set to null. A reference with no key is never changed.
     */
    inline fun <R : Any, reified T : Any> ReferenceStore<R>.resolve(reference: Reference<R, T>, clearIfNotFound: Boolean = false): T? {
        return reference.reference?.let { ref ->
            val value = this[T::class, ref]
            if ((null != value || clearIfNotFound) && reference is ResolvableReference<*, *>) {
                @Suppress("UNCHECKED_CAST")
                (reference as ResolvableReference<R, T>).resolved = value
            }
            value
        }
    }
}

/**
 * References can be qualified (string separated by '.')
 * when ref {
 *   is qualified -> find store from root
 *   else -> find in local, if null find first from root down
 * }
 */
class HierarchicalReferenceStoreByHashMap<R : Any>(
    override val parentReferenceStore: HierarchicalReferenceStore<R>?,
    override val identity: R
) : HierarchicalReferenceStore<R> {

    init {
        parentReferenceStore?.registerChild(this)
    }

    override val rootReferenceStore: HierarchicalReferenceStore<R>
        get() = when {
            null == parentReferenceStore -> this
            else -> parentReferenceStore.rootReferenceStore
        }
    private val _children = mutableMapOf<R, HierarchicalReferenceStore<R>>()
    private val _map = HashMap<KClass<*>, HashMap<R, Any>>()
    private val _ownerForReferenceType = HashMap<KClass<*>,ReferenceStore<R>>()

    override fun <T : Any> get(clazz: KClass<T>, reference: R): T? =
        _map[clazz]?.get(reference) as? T

    /**
     * Setting `null` removes the entry. The store stays the owner of the type even when it holds no objects of it.
     */
    override fun <T : Any> set(clazz: KClass<T>, reference: R, value: T?) {
        when (value) {
            null -> _map[clazz]?.remove(reference)
            else -> {
                rootReferenceStore.setOwnerForReferenceType(clazz, this) // throws if another store holds this type
                val clsMap = _map[clazz] ?: let { HashMap<R, Any>().also { _map[clazz] = it } }
                clsMap[reference] = value
            }
        }
    }

    override fun registerChild(child: HierarchicalReferenceStore<R>) {
        _children[child.identity] = child
    }

    override fun <T : Any> getOwnerForReferenceType(clazz: KClass<T>): ReferenceStore<R>? =
        _ownerForReferenceType[clazz]

    override fun <T : Any> setOwnerForReferenceType(clazz: KClass<T>, store: ReferenceStore<R>) {
        val existing = _ownerForReferenceType[clazz]
        when {
            null == existing -> _ownerForReferenceType[clazz] = store
            existing === store -> Unit
            else -> error("Objects of type '${clazz.simpleName}' are already held by store '$existing', so store '$store' can't also hold them")
        }
    }

    override fun <T : Any> findInTree(clazz: KClass<T>, reference: R): T? {
        val root = rootReferenceStore
        if (root !== this) return root.findInTree(clazz, reference)

        _ownerForReferenceType[clazz]?.get(clazz, reference)?.let { return it }

        // Not found under its own type: look for an object of a subtype stored under another type.
        val matches = mutableListOf<Any>()
        for ((type, store) in _ownerForReferenceType) {
            if (type == clazz) continue
            @Suppress("UNCHECKED_CAST")
            val value = store.get(type as KClass<Any>, reference)
            if (null != value && clazz.isInstance(value) && matches.none { it === value }) matches.add(value)
        }
        @Suppress("UNCHECKED_CAST")
        return when (matches.size) {
            0 -> null
            1 -> matches[0] as T
            else -> error("Reference '$reference' to a '${clazz.simpleName}' matches more than one object: $matches")
        }
    }

    override fun toString(): String = "HierarchicalReferenceStore '$identity'"

//    override fun <T : Any> getQualifiedReference(clazz: KClass<T>, qualifiedReference: List<R>): T? = when {
//        2 > qualifiedReference.size -> error("A Reference needs the identity of its store + its own identity, got: '$qualifiedReference'")
//        identity != qualifiedReference[0] -> error("Reference '$qualifiedReference' does not match the identity of store '$this'!")
//        2 == qualifiedReference.size -> _map[clazz]?.get(qualifiedReference[1] as R) as? T
//        else -> _children[qualifiedReference[1]]?.getQualifiedReference(clazz, qualifiedReference.drop(1))
//    }
}

object HierarchicalReferenceStoreExt {
    /**
     * returns the object fo the given reference if found.
     * Also, if the reference is mutable, will resolve the reference
     */
//    inline fun <R : Any, reified T : Any> HierarchicalReferenceStore<R>.resolveHierarchialy(reference: Reference<R, T>): T? {
//        return reference.reference?.let { ref ->
//            val qualifiedRef = reference.storeIdentity + ref
//            val value = getQualifiedReference(T::class, qualifiedRef)
//            value?.let { v ->
//                if (reference is MutableReference) {
//                    reference.set(ref, v)
//                }
//            }
//            value
//        }
//    }

    /**
     * Finds the object for the given reference anywhere in the tree of stores (see [HierarchicalReferenceStore.findInTree]).
     * Also, if the reference is a [ResolvableReference] and the object is found, sets it as the resolved value.
     *
     * If nothing is found, the reference's resolved value is left as it is, unless [clearIfNotFound] is true:
     * then it is set to null. A reference with no key is never changed.
     */
    inline fun <R : Any, reified T : Any> HierarchicalReferenceStore<R>.resolve(reference: Reference<R, T>, clearIfNotFound: Boolean = false): T? {
        return reference.reference?.let { ref ->
            val value = rootReferenceStore.findInTree(T::class, ref)
            if ((null != value || clearIfNotFound) && reference is ResolvableReference<*, *>) {
                @Suppress("UNCHECKED_CAST")
                (reference as ResolvableReference<R, T>).resolved = value
            }
            value
        }
    }
}