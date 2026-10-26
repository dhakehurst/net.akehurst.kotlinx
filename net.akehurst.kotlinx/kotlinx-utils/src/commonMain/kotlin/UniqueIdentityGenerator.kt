/*
 * Copyright (C) 2024 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Generates identities of the form `<commonPrefix><prefix>_<number>`, numbered separately per prefix.
 * Safe to call from several threads.
 */
@OptIn(ExperimentalAtomicApi::class)
class UniqueIdentityGenerator(
    val commonPrefix:String = ""
) {

    companion object {
        /** Goes between the prefix and the number, so different prefixes can never give the same identity. */
        const val SEPARATOR = "_"
        val GLOBAL = UniqueIdentityGenerator()
    }

    // The map is never changed in place; each update swaps in a new map, so concurrent calls never share a number.
    private val next = AtomicReference(emptyMap<String, Long>())

    fun generate(prefix: String = "id"): String {
        val p = commonPrefix+prefix
        while (true) {
            val current = next.load()
            val num = current[p] ?: 0L
            if (next.compareAndSet(current, current + (p to num + 1))) {
                return "${p}${SEPARATOR}${num}"
            }
        }
    }

}
