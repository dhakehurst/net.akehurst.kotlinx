/**
 * Copyright (C) 2026 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

package net.akehurst.kotlinx.secure.storage


import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.akehurst.kotlinx.filesystem.api.FileHandle
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

interface NakSerializer<T> {
    val defaultValue: T
    fun write(value: T): String
    fun read(data: String): T
}
/*
class NakFileStorage<T>(
    private val file: FileHandle,
    val serializer: NakSerializer<T>
) : Storage<T> {
    override fun createConnection(): StorageConnection<T> = NakFileStorageConnection(file, serializer)
}

internal class NakFileStorageConnection<T>(
    private val file: FileHandle,
    private val serializer: NakSerializer<T>
) : StorageConnection<T> {

    private val mutex = Mutex()

    override val coordinator: InterProcessCoordinator = NakProcessCoordinator(file)

    override suspend fun <R> readScope(block: suspend ReadScope<T>.(locked: Boolean) -> R): R {
        val lock = mutex.tryLock()
        return try {
            block.invoke(NakFileReadScope(file, serializer), lock)
        } finally {
            if (lock) {
                mutex.unlock()
            }
        }
    }

    override suspend fun writeScope(block: suspend WriteScope<T>.() -> Unit) {
        mutex.withLock {
            block.invoke(NakFileWriteScope(file, serializer))
        }
    }

    override fun close() {
    }
}


@OptIn(ExperimentalAtomicApi::class)
internal class NakProcessCoordinator(
    private val file: FileHandle
) : InterProcessCoordinator {

    private val mutex = Mutex()

    @OptIn(ExperimentalAtomicApi::class)
    private val version = AtomicInt(0)

    override val updateNotifications: Flow<Unit> = flow {}
    override suspend fun getVersion(): Int = version.load()
    override suspend fun incrementAndGetVersion(): Int = version.incrementAndFetch()
    override suspend fun <T> lock(block: suspend () -> T): T = mutex.withLock { block() }
    override suspend fun <T> tryLock(block: suspend (Boolean) -> T): T {
        val locked: Boolean = mutex.tryLock()
        try {
            return block(locked)
        } finally {
            if (locked) {
                mutex.unlock()
            }
        }
    }
}

@OptIn(ExperimentalAtomicApi::class)
internal open class NakFileReadScope<T>(
    protected val file: FileHandle,
    protected val serializer: NakSerializer<T>,
) : ReadScope<T> {

    private val closed = AtomicBoolean(false)

    override suspend fun readData(): T {
        check(!closed.load()) { "This ReadScope is closed." }
        val serialised = file.readContent()
        return serialised?.let { serializer.read(serialised) } ?: serializer.defaultValue
    }

    override fun close() {
        closed.store(true)
    }
}

internal class NakFileWriteScope<T>(
    file: FileHandle,
    serializer: NakSerializer<T>,
) : NakFileReadScope<T>(file, serializer), WriteScope<T> {
    override suspend fun writeData(value: T) {
        file.writeContent(serializer.write(value))
    }
}*/