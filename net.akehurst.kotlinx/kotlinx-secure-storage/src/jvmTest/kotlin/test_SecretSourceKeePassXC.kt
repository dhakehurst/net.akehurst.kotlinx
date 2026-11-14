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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A fake KeePassXC browser server, speaking the protocol as described in
 * https://github.com/keepassxreboot/keepassxc-browser/blob/develop/keepassxc-protocol.md
 */
private class FakeKeePassXC {
    data class Entry(val uuid: String, val url: String, val login: String, var password: String)

    val entries = mutableListOf<Entry>()
    var locked = false
    var declineAssociation = false
    var declineDelete = false
    var associations = 0
    var updates = 0
    private val knownIds = mutableMapOf<String, String>() // id -> idKey

    private val dir = Files.createTempDirectory("kpxc")
    val path: String = dir.resolve("server.sock").toString()
    private val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).also { it.bind(UnixDomainSocketAddress.of(path)) }

    init {
        thread(isDaemon = true) {
            try {
                while (true) {
                    val ch = server.accept()
                    thread(isDaemon = true) { serve(ch) }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun serve(ch: SocketChannel) {
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
                    fun reply(content: JsonObject, success: String = "true") {
                        val c = JsonObject(content + mapOf("nonce" to JsonPrimitive(SecretSourceKeePassXC.b64(respNonce)), "success" to JsonPrimitive(success)))
                        send(buildJsonObject {
                            put("action", action)
                            put("message", SecretSourceKeePassXC.b64(SecretSourceKeePassXC.box(c.toString().encodeToByteArray(), respNonce, clientPub!!, serverSec)))
                            put("nonce", SecretSourceKeePassXC.b64(respNonce))
                        })
                    }

                    fun fail(msg: String, code: String) = send(buildJsonObject { put("action", action); put("error", msg); put("errorCode", code) })
                    if (locked) {
                        fail("Database not opened", "1"); continue
                    }
                    when (action) {
                        "associate" -> if (declineAssociation) fail("Action cancelled or denied", "6") else {
                            associations++
                            val id = "client$associations"
                            knownIds[id] = inner["idKey"]!!.jsonPrimitive.content
                            reply(buildJsonObject { put("id", id); put("hash", "h") })
                        }

                        "test-associate" -> {
                            val id = inner["id"]!!.jsonPrimitive.content
                            if (knownIds[id] == inner["key"]!!.jsonPrimitive.content) reply(buildJsonObject { put("id", id) }) else fail("Association failed", "8")
                        }

                        "get-logins" -> {
                            val key = inner["keys"]!!.jsonArray[0].jsonObject
                            if (knownIds[key["id"]!!.jsonPrimitive.content] != key["key"]!!.jsonPrimitive.contentOrNull) {
                                fail("Association failed", "8")
                            } else {
                                val found = synchronized(entries) { entries.filter { it.url == inner["url"]!!.jsonPrimitive.content } }
                                if (found.isEmpty()) fail("No logins found", "15") else reply(buildJsonObject {
                                    put("count", found.size)
                                    putJsonArray("entries") { found.forEach { e -> addJsonObject { put("login", e.login); put("name", e.login); put("password", e.password); put("uuid", e.uuid) } } }
                                })
                            }
                        }

                        "set-login" -> {
                            val uuid = inner["uuid"]?.jsonPrimitive?.contentOrNull
                            synchronized(entries) {
                                val existing = uuid?.let { u -> entries.firstOrNull { it.uuid == u } }
                                if (null != existing) {
                                    existing.password = inner["password"]!!.jsonPrimitive.content
                                    updates++
                                } else {
                                    entries += Entry("uuid${entries.size + 1}", inner["url"]!!.jsonPrimitive.content, inner["login"]!!.jsonPrimitive.content, inner["password"]!!.jsonPrimitive.content)
                                }
                            }
                            reply(buildJsonObject {})
                        }

                        "delete-entry" -> {
                            val uuid = inner["uuid"]!!.jsonPrimitive.content
                            if (declineDelete) reply(buildJsonObject {}, success = "false") else {
                                synchronized(entries) { entries.removeAll { it.uuid == uuid } }
                                reply(buildJsonObject {})
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

class test_SecretSourceKeePassXC {

    private val server = FakeKeePassXC().also {
        it.entries += FakeKeePassXC.Entry("u1", "https://rm.example.com", "david", "s3cret")
        it.entries += FakeKeePassXC.Entry("u2", "https://two.example.com", "a", "1")
        it.entries += FakeKeePassXC.Entry("u3", "https://two.example.com", "b", "2")
    }

    @AfterTest
    fun after() = server.close()

    private fun sut() = SecretSourceKeePassXC(transportFactory = { KeePassXCTransportSocket(server.path) })

    @Test
    fun reads_the_password_of_the_matching_entry() = runTest {
        val sut = sut()
        assertEquals("s3cret", sut.read("https://rm.example.com"))
        assertEquals(1, server.associations)
        sut.close()
    }

    @Test
    fun no_matching_entry_gives_null() = runTest {
        assertNull(sut().read("https://other.example.com"))
    }

    @Test
    fun several_matching_entries_fail_unless_a_login_chooses_one() = runTest {
        val sut = sut()
        assertFailsWith<KeePassXCRequestException> { sut.read("https://two.example.com") }
        assertEquals("2", sut.read("https://two.example.com b"))
        assertNull(sut.read("https://two.example.com c"))
    }

    @Test
    fun the_association_is_kept_for_the_session_only() = runTest {
        val sut = sut()
        sut.read("https://rm.example.com")
        sut.close()
        sut.read("https://rm.example.com")
        assertEquals(1, server.associations, "the same instance reconnects without asking again")
        sut().read("https://rm.example.com")
        assertEquals(2, server.associations, "a new session asks again")
    }

    @Test
    fun write_creates_an_entry_and_then_updates_it() = runTest {
        val sut = sut()
        val key = "https://app.invalid/token/service app"
        sut.write(key, "t1")
        assertEquals(listOf("app"), server.entries.filter { it.url == "https://app.invalid/token/service" }.map { it.login })
        assertEquals("t1", sut.read(key))
        sut.write(key, "t2")
        assertEquals(1, server.entries.count { it.url == "https://app.invalid/token/service" }, "updated, not duplicated")
        assertEquals(1, server.updates)
        assertEquals("t2", sut.read(key))
    }

    @Test
    fun write_without_a_login_uses_the_identity() = runTest {
        val sut = sut()
        sut.write("https://new.example.com", "p")
        assertEquals(listOf(SecretSourceKeePassXC.SOURCE), server.entries.filter { it.url == "https://new.example.com" }.map { it.login })
    }

    @Test
    fun remove_deletes_the_entry() = runTest {
        val sut = sut()
        sut.write("https://x.example.com app", "t")
        sut.remove("https://x.example.com app")
        assertNull(sut.read("https://x.example.com app"))
        sut.remove("https://x.example.com app") // nothing there: no error
    }

    @Test
    fun a_declined_remove_fails() = runTest {
        val sut = sut()
        sut.write("https://x.example.com app", "t")
        server.declineDelete = true
        assertFailsWith<KeePassXCDeclinedException> { sut.remove("https://x.example.com app") }
    }

    @Test
    fun not_running_locked_and_declined_are_told_apart() = runTest {
        assertFailsWith<KeePassXCNotAvailableException> { SecretSourceKeePassXC(transportFactory = { KeePassXCTransportSocket(server.path + ".none") }).read("https://rm.example.com") }
        server.declineAssociation = true
        assertFailsWith<KeePassXCDeclinedException> { sut().read("https://rm.example.com") }
        server.declineAssociation = false
        val sut = sut()
        sut.read("https://rm.example.com")
        server.locked = true
        val e = assertFailsWith<KeePassXCLockedException> { sut.read("https://rm.example.com") }
        assertTrue(e.message!!.contains("locked"))
    }

    @Test
    fun keys_are_split_into_url_and_login() {
        assertEquals("https://a.example.com" to null, SecretSourceKeePassXC.parseKey("https://a.example.com"))
        assertEquals("https://a.example.com" to "bob", SecretSourceKeePassXC.parseKey(" https://a.example.com bob "))
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
