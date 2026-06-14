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

import net.akehurst.kotlinx.utils.ReferenceStoreExt.resolve
import kotlin.reflect.KClass

interface ReferenceStore<R : Any> {
    operator fun <T : Any> get(clazz: KClass<T>, reference: R): T?
    operator fun <T : Any> set(clazz: KClass<T>, reference: R, value: T?)
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
    fun <T : Any> setOwnerForReferenceType(clazz: KClass<T>, store: ReferenceStore<R>)

    //fun <T : Any> getQualifiedReference(clazz: KClass<T>, qualifiedReference: List<R>): T?
}

class ReferenceStoreByHashMap<R : Any> : ReferenceStore<R> {
    private val _map = HashMap<KClass<*>, HashMap<R, Any>>()

    override fun <T : Any> get(clazz: KClass<T>, reference: R): T? = _map[clazz]?.get(reference) as? T

    override fun <T : Any> set(clazz: KClass<T>, reference: R, value: T?) {
        value?.let {
            val clsMap = _map[clazz] ?: let { HashMap<R, Any>().also { _map[clazz] = it } }
            clsMap[reference] = value
        }
    }
}

object ReferenceStoreExt {
    /**
     * returns the object fo the given reference if found.
     * Also, if the reference is mutable, will resolve the reference
     */
    inline fun <R : Any, reified T : Any> ReferenceStore<R>.resolve(reference: Reference<R, T>): T? {
        return reference.reference?.let { ref ->
            val value = this[T::class, ref]
            value?.let { v ->
                if (reference is MutableReference) {
                    reference.set(ref, v)
                }
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

    override fun <T : Any> get(clazz: KClass<T>, reference: R): T? = when {
        reference is String -> when {
//            reference is List<*> -> rootReferenceStore.getQualifiedReference(clazz, reference as List<R>) as T?
            else -> _map[clazz]?.get(reference) as? T
        }

        else -> _map[clazz]?.get(reference) as? T
    }

    override fun <T : Any> set(clazz: KClass<T>, reference: R, value: T?) {
        value?.let {
            rootReferenceStore.setOwnerForReferenceType(clazz, this) //TODO: decide should this be an auto registration or predefined!
            val clsMap = _map[clazz] ?: let { HashMap<R, Any>().also { _map[clazz] = it } }
            clsMap[reference] = value
        }
    }

    override fun registerChild(child: HierarchicalReferenceStore<R>) {
        _children[child.identity] = child
    }

    override fun <T : Any> getOwnerForReferenceType(clazz: KClass<T>): ReferenceStore<R>? =
        _ownerForReferenceType[clazz]

    override fun <T : Any> setOwnerForReferenceType(clazz: KClass<T>, store: ReferenceStore<R>) {
        _ownerForReferenceType[clazz] = store
    }

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

    inline fun <R : Any, reified T : Any> HierarchicalReferenceStore<R>.resolve(reference: Reference<R, T>): T? {
        val store = rootReferenceStore.getOwnerForReferenceType(T::class)
        return store?.resolve(reference)
    }
}