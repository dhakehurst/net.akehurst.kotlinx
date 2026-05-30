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
// --- Reference values ---

interface Reference<R : Any, out T : Any> {
    val storeIdentity:List<R>
    val reference: R?
    val resolved: T?
}

interface MutableReference<R : Any, T : Any> : Reference<R, T> {
    override var reference: R?
    fun set(ref: R, value: T)
    fun clear()
}

object ReferenceExt {
    inline val <REF : Any, T : Any> Reference<REF, T>.mutable get() = this as MutableReference
}

abstract class MutableReferenceAbstract<R : Any, T : Any>() : MutableReference<R, T> {
    override var resolved: T? = null
    override fun set(ref: R, value: T) {
        reference = ref
        resolved = value
    }

    override fun clear() {
        reference = null
        resolved = null
    }
}

data class MutableReferenceDefault<R : Any, T : Any>(
    override val storeIdentity: List<R>,
    override var reference: R? = null,
) : MutableReferenceAbstract<R, T>()

data class ManagedReference<R : Any, T : Any>(
    override val storeIdentity: List<R>,
    override var reference: R? = null,
    private val name: String,
    private val expectedType: KClass<*>,
    private val onChanged: ((R?, T?) -> Unit)? = null,
) : MutableReferenceAbstract<R, T>() {

    private fun validateType(element: T) {
        if (!expectedType.isInstance(element)) {
            throw IllegalArgumentException(
                "Managed List violation on '$name': Expected '${expectedType.simpleName}', but got '${element::class.simpleName}'."
            )
        }
    }

    override fun set(ref: R, value: T) {
        validateType(value)
        super.set(ref, value)
        onChanged?.invoke(reference, resolved)
    }

    override fun clear() {
        super.clear()
        onChanged?.invoke(reference, resolved)
    }
}
