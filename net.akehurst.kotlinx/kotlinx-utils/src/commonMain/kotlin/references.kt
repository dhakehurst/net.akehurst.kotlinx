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

interface ReferenceStore<REF : Any> {
    operator fun <TO : Any> get(clazz: KClass<TO>, reference: REF): TO?
    operator fun <TO : Any> set(clazz: KClass<TO>, reference: REF, value:TO?)
}

/**
 * returns the object fo the given reference if found.
 * Also, if the reference is mutable, will resolve the reference
 */
inline fun <REF : Any, reified TO : Any> ReferenceStore<REF>.resolve(reference: Reference<REF, TO>): TO? {
    return reference.reference?.let { ref ->
        val value = this[TO::class, ref]
        value?.let { v ->
            if (reference is MutableReference) {
                reference.set(ref, v)
            }
        }
        value
    }
}

interface Reference<REF : Any, out TO : Any> {
    val reference: REF?
    val resolved: TO?
}

interface MutableReference<REF : Any, TO : Any> : Reference<REF, TO> {
    override var reference: REF?
    fun set(ref: REF, value: TO)
    fun clear()
}

inline val <REF : Any, TO : Any> Reference<REF, TO>.mutableReference get() = this as MutableReference

data class MutableReferenceDefault<REF : Any, TO : Any>(
    override var reference: REF? = null,
) : MutableReference<REF, TO> {

    override var resolved: TO? = null

    override fun set(ref: REF, value: TO) {
        reference = ref
        resolved = value
    }

    override fun clear() {
        reference = null
        resolved = null
    }
}