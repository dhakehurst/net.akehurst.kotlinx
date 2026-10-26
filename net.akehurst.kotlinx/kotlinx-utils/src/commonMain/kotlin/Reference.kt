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
 * One reference equals another if the reference is the same.
 * Every implementation must follow this, comparing with any other Reference, using [referenceEquals]
 * and [referenceHashCode], so equality is the same whichever side it is called on.
 */
interface Reference<R : Any, out T : Any> {
    //val storeIdentity:List<R>
    val reference: R?
    val resolved: T?
}

/**
 * A reference whose key is fixed, but whose resolved object can be set, e.g. by a store's resolve.
 * Use these for references held in collections, so their key (and so their hash code) can't change.
 */
interface ResolvableReference<R : Any, T : Any> : Reference<R, T> {
    override var resolved: T?
}

/**
 * A reference whose key can also be changed. Use these for a single-valued reference property,
 * never as an element of a collection or a map key, because equality and hash code follow the key.
 */
interface MutableReference<R : Any, T : Any> : ResolvableReference<R, T> {
    override var reference: R?
    fun set(ref: R?, value: T?)
    fun clear()
}

/** Equality for [Reference] implementations: equal to any other Reference with the same key. */
fun Reference<*, *>.referenceEquals(other: Any?): Boolean = other is Reference<*, *> && other.reference == this.reference

/** Hash code for [Reference] implementations, consistent with [referenceEquals]. */
fun Reference<*, *>.referenceHashCode(): Int = reference.hashCode()

/** The type check used by the managed references: [element] must be an instance of [expectedType]. */
internal fun checkManagedReferenceType(name: String, expectedType: KClass<*>, element: Any) {
    if (!expectedType.isInstance(element)) {
        throw IllegalArgumentException(
            "Managed Reference violation on '$name': Expected reference to '${expectedType.simpleName}', but got '${element::class.simpleName}'."
        )
    }
}

data class ReferenceImmutable<R : Any, T : Any>(
    override val reference: R?
) : Reference<R, T> {
    constructor(reference: R?, resolved: T?) : this(reference) {
        this.resolved = resolved
    }

    // Set only by the constructor; kept out of the primary constructor so equality stays based on reference only.
    override var resolved: T? = null
        private set

    override fun equals(other: Any?): Boolean = referenceEquals(other)
    override fun hashCode(): Int = referenceHashCode()
}


/** A reference with a fixed key whose resolved object can be set. */
class ResolvableReferenceDefault<R : Any, T : Any>(
    override val reference: R?,
    override var resolved: T? = null
) : ResolvableReference<R, T> {
    override fun equals(other: Any?): Boolean = referenceEquals(other)
    override fun hashCode(): Int = referenceHashCode()
    override fun toString(): String = "ResolvableReference($reference)"
}

/** A reference with a fixed key whose resolved object can be set, and must be an instance of [expectedType]. */
class ManagedResolvableReference<R : Any, T : Any>(
    override val reference: R?,
    private val name: String,
    private val expectedType: KClass<*>,
    resolved: T? = null
) : ResolvableReference<R, T> {

    override var resolved: T? = resolved?.also { checkManagedReferenceType(name, expectedType, it) }
        set(value) {
            value?.let { checkManagedReferenceType(name, expectedType, it) }
            field = value
        }

    override fun equals(other: Any?): Boolean = referenceEquals(other)
    override fun hashCode(): Int = referenceHashCode()
    override fun toString(): String = "ManagedResolvableReference($reference)"
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

    override fun equals(other: Any?): Boolean = referenceEquals(other)
    override fun hashCode(): Int = referenceHashCode()
}

class ManagedReference<R : Any, T : Any>(
    // override val storeIdentity: List<R>,
    reference: R? = null,
    private val name: String,
    private val expectedType: KClass<*>,
    private val onChanged: ((old: Reference<R, T>, new: Reference<R, T>) -> Unit)? = null
) : MutableReference<R, T> {

    private var _reference: R? = reference
    private var _resolved: T? = null

    /**
     * Assigning a different key is the same as `set(newKey, null)`: the object resolved for the old key is
     * dropped, and onChanged is called. Assigning the same key does nothing.
     */
    override var reference: R?
        get() = _reference
        set(value) {
            if (value != _reference) set(value, null)
        }

    override var resolved: T?
        get() = _resolved
        set(value) {
            set(reference, value)
        }

    /**
     * onChanged is given fixed snapshots of the reference before and after the change, never this object,
     * so a callback can safely put them in a collection.
     */
    override fun set(ref: R?, value: T?) {
        if (this._resolved != value || this.reference != ref) {
            val old = ReferenceImmutable(reference, resolved)
            value?.let { checkManagedReferenceType(name, expectedType, it) }
            this._reference = ref
            this._resolved = value
            onChanged?.invoke(old, ReferenceImmutable(ref, value))
        } else {
            // already set
        }
    }

    override fun clear() = set(null, null)

    override fun equals(other: Any?): Boolean = referenceEquals(other)
    override fun hashCode(): Int = referenceHashCode()

    override fun toString(): String = "ManagedReference($reference)"
}
