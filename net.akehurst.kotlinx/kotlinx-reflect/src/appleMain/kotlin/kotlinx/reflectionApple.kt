/**
 * Copyright (C) 2019 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

package net.akehurst.kotlinx.reflect

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

actual fun KFunction<*>.isSuspend(): Boolean =
    KotlinxReflect.isSuspend(this) ?: error("Function '${this.name}' is not registered with KotlinxReflect, cannot tell if it is a suspend function")

actual fun <T : Any> proxyFor(forInterface: KClass<*>, invokeMethod: (handler: Any, proxy: Any?, callable: KCallable<*>, methodName: String, args: Array<out Any>) -> Any?): T {
    TODO("Native proxyFor")
}

internal actual fun defaultForceRegistry(): Boolean = false

@OptIn(ExperimentalAtomicApi::class)
private val registryLock = AtomicInt(0)

// a simple spin lock; registration is short and rarely contended
@OptIn(ExperimentalAtomicApi::class)
internal actual fun withRegistryLock(block: () -> Unit) {
    while (!registryLock.compareAndSet(0, 1)) {
        // wait for the other thread to finish registering
    }
    try {
        block()
    } finally {
        registryLock.store(0)
    }
}

// Native uses the KotlinxReflect registry
internal actual val platformBackend: ReflectionBackend? = null
