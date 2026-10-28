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

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * A fake KeePassXC browser server, speaking the protocol as described in
 * https://github.com/keepassxreboot/keepassxc-browser/blob/develop/keepassxc-protocol.md
 */
private class FakeKeePassXC(val entries: Map<String, List<Pair<String, String>>>) {
    val dir = Files.createTempDirectory("kpxc")
    val path = dir.resolve("server.sock").toString()
    private val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).also { it.bind(UnixDomainSocketAddress.of(path)) }
    var associations = 0
    val knownIds = mutableMapOf<String, String>() // id -> idKey

    private val t = thread(isDaemon = true) {
        try {
            while (true) {
                val ch = server.accept()
                thread(isDaemon = true) { serve(ch) }
            }
        } catch (_: Throwable) {
        }
    }

    private fun serve(ch: java.nio.channels.SocketChannel) {
        val (serverPub, serverSec) = SecretSourceKeePassXC.keyPair()
        var clientPub: ByteArray? = null
        val splitter = JsonObjectSplitter()
        val buf = ByteBuffer.allocate(1024 * 1024)
        fun send(o: JsonObject) {
            val b = ByteBuffer.wrap(o.toString().encodeToByteArray()); while (b.hasRemaining()) ch.write(b)
        }
        try {
            while (true) {
                val n = ch.read(buf); if (n < 0) return
                buf.flip(); splitter.add(Charsets.UTF_8.decode(buf).toString()); buf.compact()
                while (true) {
                    val m = splitter.next()?.let { Json.parseToJsonElement(it).jsonObject } ?: break
                    val action = m["action"]!!.jsonPrimitive.content
                    val nonce = SecretSourceKeePassXC.unb64(m["nonce"]!!.jsonPrimitive.content)
                    val respNonce = nonce.copyOf().also { increment(it) }
                    if (action == "change-public-keys") {
                        clientPub = SecretSourceKeePassXC.unb64(m["publicKey"]!!.jsonPrimitive.content)
                        send(buildJsonObject {
                            put("action", action); put("version", "2.7.9"); put("publicKey", SecretSourceKeePassXC.b64(serverPub))
                            put("nonce", SecretSourceKeePassXC.b64(respNonce)); put("success", "true")
                        })
                        continue
                    }
                    val inner = Json.parseToJsonElement(
                        SecretSourceKeePassXC.unbox(SecretSourceKeePassXC.unb64(m["message"]!!.jsonPrimitive.content), nonce, clientPub!!, serverSec).decodeToString()
                    ).jsonObject
                    fun reply(content: JsonObject) {
                        val c = JsonObject(content + mapOf("nonce" to kotlinx.serialization.json.JsonPrimitive(SecretSourceKeePassXC.b64(respNonce)), "success" to kotlinx.serialization.json.JsonPrimitive("true")))
                        send(buildJsonObject {
                            put("action", action)
                            put("message", SecretSourceKeePassXC.b64(SecretSourceKeePassXC.box(c.toString().encodeToByteArray(), respNonce, clientPub!!, serverSec)))
                            put("nonce", SecretSourceKeePassXC.b64(respNonce))
                        })
                    }
                    fun fail(msg: String, code: String) = send(buildJsonObject { put("action", action); put("error", msg); put("errorCode", code) })
                    when (action) {
                        "associate" -> {
                            associations++
                            val id = "client$associations"
                            knownIds[id] = inner["idKey"]!!.jsonPrimitive.content
                            reply(buildJsonObject { put("id", id); put("hash", "h") })
                        }

                        "test-associate" -> {
                            val id = inner["id"]!!.jsonPrimitive.content
                            if (knownIds[id] == inner["key"]!!.jsonPrimitive.content) reply(buildJsonObject { put("id", id) }) else fail("Association failed", "9")
                        }

                        "get-logins" -> {
                            val key = inner["keys"]!!.jsonArray[0].jsonObject
                            if (knownIds[key["id"]!!.jsonPrimitive.content] != key["key"]!!.jsonPrimitive.contentOrNull) {
                                fail("Association failed", "9")
                            } else {
                                val found = entries[inner["url"]!!.jsonPrimitive.content].orEmpty()
                                if (found.isEmpty()) fail("No logins found", "15") else reply(buildJsonObject {
                                    put("count", found.size)
                                    putJsonArray("entries") { found.forEach { (l, p) -> addJsonObject { put("login", l); put("name", l); put("password", p); put("uuid", "u") } } }
                                })
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun increment(n: ByteArray) {
        for (i in n.indices) {
            n[i] = (n[i] + 1).toByte(); if (n[i] != 0.toByte()) return
        }
    }

    fun close() = server.close()
}

private class MemoryStorage : SecureStorage {
    val map = mutableMapOf<String, Any>()
    override suspend fun write(key: String, value: Any) { map[key] = value }
    override suspend fun read(key: String): Any? = map[key]
}

class test_SecretSourceKeePassXC {

    private val server = FakeKeePassXC(
        mapOf(
            "https://rm.example.com" to listOf("david" to "s3cret"),
            "https://two.example.com" to listOf("a" to "1", "b" to "2"),
        )
    )

    @AfterTest
    fun after() = server.close()

    private fun sut(storage: SecureStorage? = null) = SecretSourceKeePassXC(associationStorage = storage, transportFactory = { KeePassXCTransportSocket(server.path) })

    @Test
    fun reads_the_password_of_the_matching_entry() = runTest {
        val sut = sut()
        assertEquals("s3cret", sut.read("https://rm.example.com"))
        assertEquals(1, server.associations)
        sut.close()
    }

    @Test
    fun no_matching_entry_gives_null() = runTest {
        val sut = sut()
        assertNull(sut.read("https://other.example.com"))
        sut.close()
    }

    @Test
    fun several_matching_entries_fails() = runTest {
        val sut = sut()
        assertFailsWith<IllegalStateException> { sut.read("https://two.example.com") }
        sut.close()
    }

    @Test
    fun stored_association_is_reused() = runTest {
        val storage = MemoryStorage()
        sut(storage).also { it.read("https://rm.example.com"); it.close() }
        val second = sut(storage)
        assertEquals("s3cret", second.read("https://rm.example.com"))
        // the user approved only once
        assertEquals(1, server.associations)
        second.close()
    }

    @Test
    fun json_splitter_handles_split_and_joined_messages() {
        val s = JsonObjectSplitter()
        s.add("""{"a":"}{\"x"}{"b"""")
        s.add(""":1}""")
        assertEquals("""{"a":"}{\"x"}""", s.next())
        assertEquals("""{"b":1}""", s.next())
        assertNull(s.next())
    }
}
