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

package net.akehurst.kotlinx.collections

inline val <E> Collection<E>.mutableCollection get() = this as MutableCollection
inline val <E> List<E>.mutableList get() = this as MutableList
inline val <E> Set<E>.mutableSet get() = this as MutableSet
inline val <E> OrderedSet<E>.mutableOrderedSet get() = this as MutableOrderedSet