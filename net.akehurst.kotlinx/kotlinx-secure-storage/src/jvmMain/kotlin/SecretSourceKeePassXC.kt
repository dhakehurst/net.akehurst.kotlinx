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

import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import java.io.File
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.Base64

/**
 * A SecretSource that reads passwords from a running KeePassXC, through its browser-integration protocol
 * (the one the KeePassXC-Browser extension uses). KeePassXC does the unlocking; this source never sees the database password.
 *
 * The key is a URL: the secret is the password of the KeePassXC entry whose URL matches it, e.g. secret("keepassxc", "https://rm.example.com").
 * If no entry matches, read gives null; if several match, read fails.
 *
 * The first time, KeePassXC asks the user to approve ("associate") this client. The association is kept in
 * 'associationStorage' (e.g. a SecureStorageDatastore) so that the user approves only once; without it, the user approves once per instance.
 * Browser integration must be enabled in KeePassXC (Settings > Browser Integration). JVM only.
 */
class SecretSourceKeePassXC(
    override val identity: String = "keepassxc",
    /** where the association with KeePassXC is kept, under 'associationKey'; null to keep it only in this instance */
    val associationStorage: SecureStorage? = null,
    val associationKey: String = "net.akehurst.kotlinx.secure.storage.keepassxc.association",
    /** ask KeePassXC to show its unlock dialog if the database is locked */
    val triggerUnlock: Boolean = true,
    private val transportFactory: () -> KeePassXCTransport = { KeePassXCTransport.connect() }
) : SecretSource {

    companion object {
        private val sodium by lazy { LazySodiumJava(SodiumJava()) }
        private const val NONCE_BYTES = 24
        private const val KEY_BYTES = 32
        private const val MAC_BYTES = 16
        private val b64e = Base64.getEncoder()
        private val b64d = Base64.getDecoder()

        internal fun keyPair(): Pair<ByteArray, ByteArray> {
            val pub = ByteArray(KEY_BYTES)
            val sec = ByteArray(KEY_BYTES)
            check(sodium.cryptoBoxKeypair(pub, sec)) { "Cannot create a key pair" }
            return Pair(pub, sec)
        }

        internal fun random(n: Int): ByteArray = sodium.randomBytesBuf(n)

        internal fun box(message: ByteArray, nonce: ByteArray, theirPublic: ByteArray, mySecret: ByteArray): ByteArray {
            val cipher = ByteArray(message.size + MAC_BYTES)
            check(sodium.cryptoBoxEasy(cipher, message, message.size.toLong(), nonce, theirPublic, mySecret)) { "Cannot encrypt" }
            return cipher
        }

        internal fun unbox(cipher: ByteArray, nonce: ByteArray, theirPublic: ByteArray, mySecret: ByteArray): ByteArray {
            check(cipher.size >= MAC_BYTES) { "Cannot decrypt the message from KeePassXC" }
            val message = ByteArray(cipher.size - MAC_BYTES)
            check(sodium.cryptoBoxOpenEasy(message, cipher, cipher.size.toLong(), nonce, theirPublic, mySecret)) { "Cannot decrypt the message from KeePassXC" }
            return message
        }

        internal fun b64(bytes: ByteArray): String = b64e.encodeToString(bytes)
        internal fun unb64(str: String): ByteArray = b64d.decode(str)
    }

    private class Session(
        val transport: KeePassXCTransport,
        val clientId: String,
        val publicKey: ByteArray,
        val secretKey: ByteArray,
        val serverPublicKey: ByteArray,
        var associationId: String? = null,
        var idKey: String? = null,
    )

    private val _mutex = Mutex()
    private var _session: Session? = null

    override suspend fun read(key: String): String? = _mutex.withLock {
        withContext(Dispatchers.IO) {
            val session = session()
            val resp = request(
                session, "get-logins", buildJsonObject {
                    put("url", key)
                    putJsonArray("keys") {
                        addJsonObject {
                            put("id", session.associationId)
                            put("key", session.idKey)
                        }
                    }
                }, allowNotFound = true
            ) ?: return@withContext null
            val entries = resp["entries"] as? JsonArray ?: return@withContext null
            when (entries.size) {
                0 -> null
                1 -> entries[0].jsonObject["password"]?.jsonPrimitive?.contentOrNull
                else -> error("KeePassXC has ${entries.size} entries for '$key'; expected one")
            }
        }
    }

    /** closes the connection to KeePassXC; the next read connects again */
    fun close() {
        _session?.transport?.close()
        _session = null
    }

    override fun toString(): String = "SecretSourceKeePassXC($identity)"

    // --- protocol ---

    private suspend fun session(): Session {
        _session?.let { return it }
        val transport = transportFactory()
        try {
            val (pub, sec) = keyPair()
            val clientId = b64(random(NONCE_BYTES))
            val nonce = random(NONCE_BYTES)
            val resp = exchange(transport, buildJsonObject {
                put("action", "change-public-keys")
                put("publicKey", b64(pub))
                put("nonce", b64(nonce))
                put("clientID", clientId)
            }, "change-public-keys")
            checkSuccess(resp, "change-public-keys")
            val serverPub = unb64(resp["publicKey"]?.jsonPrimitive?.contentOrNull ?: error("KeePassXC sent no public key"))
            val session = Session(transport, clientId, pub, sec, serverPub)
            associate(session)
            _session = session
            return session
        } catch (t: Throwable) {
            transport.close()
            throw t
        }
    }

    /** uses the stored association if KeePassXC still accepts it, else asks KeePassXC (and so the user) for a new one, and stores it */
    private suspend fun associate(session: Session) {
        val stored = associationStorage?.read(associationKey)?.toString()?.split(' ')?.takeIf { it.size == 2 }
        if (null != stored) {
            val ok = runCatching {
                request(session, "test-associate", buildJsonObject {
                    put("id", stored[0])
                    put("key", stored[1])
                })
            }.isSuccess
            if (ok) {
                session.associationId = stored[0]
                session.idKey = stored[1]
                return
            }
        }
        val idKey = b64(keyPair().first)
        val resp = request(session, "associate", buildJsonObject {
            put("key", b64(session.publicKey))
            put("idKey", idKey)
        }) ?: error("KeePassXC did not associate")
        val id = resp["id"]?.jsonPrimitive?.contentOrNull ?: error("KeePassXC sent no association id")
        session.associationId = id
        session.idKey = idKey
        associationStorage?.write(associationKey, "$id $idKey")
    }

    /**
     * sends an encrypted request and returns the decrypted response.
     * Returns null if 'allowNotFound' and KeePassXC reports that no logins were found.
     */
    private suspend fun request(session: Session, action: String, content: JsonObject, allowNotFound: Boolean = false): JsonObject? {
        val nonce = random(NONCE_BYTES)
        val inner = JsonObject(mapOf("action" to JsonPrimitive(action)) + content)
        val cipher = box(inner.toString().encodeToByteArray(), nonce, session.serverPublicKey, session.secretKey)
        val outer = buildJsonObject {
            put("action", action)
            put("message", b64(cipher))
            put("nonce", b64(nonce))
            put("clientID", session.clientId)
            if (triggerUnlock) put("triggerUnlock", "true")
        }
        val resp = exchange(session.transport, outer, action)
        val error = resp["error"]?.jsonPrimitive?.contentOrNull
        if (null != error) {
            val code = resp["errorCode"]?.jsonPrimitive?.contentOrNull
            if (allowNotFound && code == ERROR_NO_LOGINS_FOUND) return null
            error("KeePassXC '$action' failed: $error (code $code)")
        }
        val msg = resp["message"]?.jsonPrimitive?.contentOrNull ?: error("KeePassXC '$action' sent no message")
        val respNonce = unb64(resp["nonce"]?.jsonPrimitive?.contentOrNull ?: error("KeePassXC '$action' sent no nonce"))
        val plain = unbox(unb64(msg), respNonce, session.serverPublicKey, session.secretKey)
        val decrypted = Json.parseToJsonElement(plain.decodeToString()).jsonObject
        checkSuccess(decrypted, action)
        return decrypted
    }

    private fun checkSuccess(resp: JsonObject, action: String) {
        val success = resp["success"]?.jsonPrimitive?.contentOrNull
        check(success == null || success == "true") { "KeePassXC '$action' did not succeed" }
    }

    /** sends 'message' and returns the next message from KeePassXC for 'action' (other messages, e.g. notifications, are skipped) */
    private fun exchange(transport: KeePassXCTransport, message: JsonObject, action: String): JsonObject {
        transport.send(message.toString())
        while (true) {
            val resp = Json.parseToJsonElement(transport.receive()).jsonObject
            if (resp["action"]?.jsonPrimitive?.contentOrNull == action) return resp
        }
    }
}

/** KeePassXC's error code for "No logins found" */
private const val ERROR_NO_LOGINS_FOUND = "15"

/**
 * The connection to KeePassXC's browser-integration socket: a Unix domain socket (Linux, macOS)
 * or a named pipe (Windows). Messages are JSON objects, sent as UTF-8 text.
 */
interface KeePassXCTransport : AutoCloseable {
    fun send(message: String)

    /** the next JSON object received */
    fun receive(): String

    companion object {
        const val SERVER_NAME = "org.keepassxc.KeePassXC.BrowserServer"

        /** the places KeePassXC puts its socket on this platform, most likely first */
        fun candidatePaths(): List<String> {
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("win") -> listOf("""\\.\pipe\${SERVER_NAME}_${System.getenv("USERNAME") ?: ""}""")
                os.contains("mac") -> listOfNotNull(System.getenv("TMPDIR")?.let { File(it, SERVER_NAME).path }, "/tmp/$SERVER_NAME")
                else -> listOfNotNull(
                    System.getenv("XDG_RUNTIME_DIR")?.let { File(it, "app/org.keepassxc.KeePassXC/$SERVER_NAME").path },
                    System.getenv("XDG_RUNTIME_DIR")?.let { File(it, SERVER_NAME).path },
                    "/tmp/$SERVER_NAME"
                )
            }
        }

        /** connects to the running KeePassXC */
        fun connect(): KeePassXCTransport {
            val paths = candidatePaths()
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("win") -> KeePassXCTransportStream.namedPipe(paths.first())
                else -> {
                    val path = paths.firstOrNull { File(it).exists() }
                        ?: error("KeePassXC is not running, or browser integration is off (no socket at ${paths.joinToString()})")
                    KeePassXCTransportSocket(path)
                }
            }
        }
    }
}

/** splits a stream of characters into top-level JSON objects */
internal class JsonObjectSplitter {
    private val _buf = StringBuilder()
    private var _depth = 0
    private var _inString = false
    private var _escape = false
    private val _complete = ArrayDeque<String>()

    fun add(text: String) {
        for (c in text) {
            if (_depth == 0 && c != '{') continue
            _buf.append(c)
            when {
                _escape -> _escape = false
                _inString && c == '\\' -> _escape = true
                c == '"' -> _inString = !_inString
                _inString -> {}
                c == '{' -> _depth++
                c == '}' -> {
                    _depth--
                    if (_depth == 0) {
                        _complete.addLast(_buf.toString())
                        _buf.clear()
                    }
                }
            }
        }
    }

    fun next(): String? = _complete.removeFirstOrNull()
}

class KeePassXCTransportSocket(path: String) : KeePassXCTransport {
    private val _channel = SocketChannel.open(StandardProtocolFamily.UNIX).also { it.connect(UnixDomainSocketAddress.of(path)) }
    private val _splitter = JsonObjectSplitter()
    private val _decoder = Charsets.UTF_8.newDecoder()
    private val _in = ByteBuffer.allocate(1024 * 1024)

    override fun send(message: String) {
        val bytes = ByteBuffer.wrap(message.encodeToByteArray())
        while (bytes.hasRemaining()) _channel.write(bytes)
    }

    override fun receive(): String {
        while (true) {
            _splitter.next()?.let { return it }
            val n = _channel.read(_in)
            check(n >= 0) { "KeePassXC closed the connection" }
            _in.flip()
            val chars = _decoder.decode(_in)
            _in.compact()
            _splitter.add(chars.toString())
        }
    }

    override fun close() = _channel.close()
}

class KeePassXCTransportStream private constructor(private val file: RandomAccessFile) : KeePassXCTransport {
    companion object {
        fun namedPipe(path: String): KeePassXCTransportStream = try {
            KeePassXCTransportStream(RandomAccessFile(path, "rw"))
        } catch (e: java.io.FileNotFoundException) {
            error("KeePassXC is not running, or browser integration is off (no pipe at $path)")
        }
    }

    private val _splitter = JsonObjectSplitter()
    private val _buf = ByteArray(1024 * 1024)

    override fun send(message: String) = file.write(message.encodeToByteArray())

    override fun receive(): String {
        while (true) {
            _splitter.next()?.let { return it }
            val n = file.read(_buf)
            check(n >= 0) { "KeePassXC closed the connection" }
            // a message is sent in one write, so a UTF-8 sequence is not split across reads in practice
            _splitter.add(_buf.decodeToString(0, n))
        }
    }

    override fun close() = file.close()
}
