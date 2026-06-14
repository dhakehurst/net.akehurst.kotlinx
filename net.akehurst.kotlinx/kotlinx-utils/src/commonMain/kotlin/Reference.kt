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

/**
 * One reference equals another if the reference is the same
 */
interface Reference<R : Any, out T : Any> {
    //val storeIdentity:List<R>
    val reference: R?
    val resolved: T?
}

interface MutableReference<R : Any, T : Any> : Reference<R, T> {
    override var reference: R?
    fun set(ref: R?, value: T?)
    fun clear()
}

data class ReferenceImmutable<R : Any, T : Any>(
    override var reference: R?
) : Reference<R, T> {
    constructor(reference: R?, resolved: T?) : this(reference) {
        this.resolved = resolved
    }

    override var resolved: T? = null
}


object ReferenceExt {
    inline val <REF : Any, T : Any> Reference<REF, T>.mutable get() = this as MutableReference
    inline val <REF : Any, T : Any> Reference<REF, T>.mutableOrNull get() = this as? MutableReference
}

data class MutableReferenceDefault<R : Any, T : Any>(
    //override val storeIdentity: List<R>,
    override var reference: R? = null,
) : MutableReference<R, T> {
    override var resolved: T? = null
    override fun set(ref: R?, value: T?) {
        reference = ref
        resolved = value
    }

    override fun clear() = set(null, null)
}

class ManagedReference<R : Any, T : Any>(
    // override val storeIdentity: List<R>,
    override var reference: R? = null,
    private val name: String,
    private val expectedType: KClass<*>,
    private val onChanged: ((old: Reference<R, T>, new: Reference<R, T>) -> Unit)? = null
) : MutableReference<R, T> {

    private var _resolved: T? = null

    override var resolved: T?
        get() = _resolved
        set(value) {
            set(reference, value)
        }

    private fun validateType(element: T) {
        if (!expectedType.isInstance(element)) {
            throw IllegalArgumentException(
                "Managed List violation on '$name': Expected '${expectedType.simpleName}', but got '${element::class.simpleName}'."
            )
        }
    }

    override fun set(ref: R?, value: T?) {
        if (this._resolved != value) {
            val old = ReferenceImmutable(reference, resolved)
            value?.let { validateType(value) }
            this.reference = ref
            this._resolved = value
            onChanged?.invoke(old, this)
        } else {
            // already set
        }
    }

    override fun clear() = set(null, null)

    override fun hashCode(): Int = reference.hashCode()
    override fun equals(other: Any?): Boolean = when (other) {
        !is Reference<*, *> -> false
        else -> other.reference == reference
    }

    override fun toString(): String = "ManagedReference($reference)"
}
