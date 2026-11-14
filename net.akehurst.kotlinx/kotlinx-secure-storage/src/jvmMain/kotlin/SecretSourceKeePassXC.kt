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
import java.io.IOException
import java.io.File
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.Base64

/** Why KeePassXC could not be used; the message is meant for the user. */
sealed class KeePassXCException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** KeePassXC is not running, its browser integration is switched off, or the connection to it was lost */
class KeePassXCNotAvailableException(message: String, cause: Throwable? = null) : KeePassXCException(message, cause)

/** the KeePassXC database is locked, and the user did not unlock it */
class KeePassXCLockedException(message: String) : KeePassXCException(message)

/** the user did not allow this application to use KeePassXC, or refused a request (e.g. an update or a removal) in KeePassXC */
class KeePassXCDeclinedException(message: String) : KeePassXCException(message)

/** KeePassXC answered in an unexpected way, e.g. several entries match one key, a field is missing, or a message cannot be decrypted */
class KeePassXCRequestException(message: String) : KeePassXCException(message)

/**
 * A SecretSource that reads, writes and removes passwords in a running KeePassXC, through its browser-integration protocol
 * (the one the KeePassXC-Browser extension uses, https://github.com/keepassxreboot/keepassxc-browser/blob/develop/keepassxc-protocol.md).
 * KeePassXC does the unlocking; this source never sees the database password.
 *
 * A key is the URL of an entry, optionally followed by one space and a login, e.g. `https://rm.example.com` or `https://rm.example.com alice`;
 * with a login, only entries with that login match. The secret is the entry's password.
 * If no entry matches, [read] gives null; if several match, it fails with a [KeePassXCRequestException].
 *
 * The first time in a session, KeePassXC asks the user to allow ("associate") this client. The association is kept in memory only
 * (it is never written anywhere), and reused while KeePassXC accepts it, so the user allows the application once per instance.
 * Browser integration must be enabled in KeePassXC (Settings > Browser Integration). JVM only.
 *
 * Failures are reported as subclasses of [KeePassXCException], each with a message meant for the user.
 */
class SecretSourceKeePassXC(
    override val identity: String = SOURCE,
    /** ask KeePassXC to show its unlock dialog if the database is locked */
    val triggerUnlock: Boolean = true,
    private val transportFactory: () -> KeePassXCTransport = { KeePassXCTransport.connect() }
) : SecretSource {

    companion object {
        /** the default identity of the KeePassXC secret source */
        const val SOURCE = "keepassxc"

        private val sodium by lazy { LazySodiumJava(SodiumJava()) }
        private const val NONCE_BYTES = 24
        private const val KEY_BYTES = 32
        private const val MAC_BYTES = 16
        private val b64e = Base64.getEncoder()
        private val b64d = Base64.getDecoder()

        // KeePassXC's error codes (src/browser/BrowserMessageBuilder.h)
        private const val ERROR_DATABASE_NOT_OPENED = "1"
        private const val ERROR_ACTION_CANCELLED_OR_DENIED = "6"
        private const val ERROR_ASSOCIATION_FAILED = "8"
        private const val ERROR_NO_LOGINS_FOUND = "15"
        private const val ERROR_ACCESS_TO_ALL_ENTRIES_DENIED = "19"

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
            if (cipher.size < MAC_BYTES) throw KeePassXCRequestException("Cannot decrypt the message from KeePassXC")
            val message = ByteArray(cipher.size - MAC_BYTES)
            if (!sodium.cryptoBoxOpenEasy(message, cipher, cipher.size.toLong(), nonce, theirPublic, mySecret)) throw KeePassXCRequestException("Cannot decrypt the message from KeePassXC")
            return message
        }

        internal fun b64(bytes: ByteArray): String = b64e.encodeToString(bytes)
        internal fun unb64(str: String): ByteArray = b64d.decode(str)

        /** the URL and the login (null if none) of 'key' */
        internal fun parseKey(key: String): Pair<String, String?> {
            val k = key.trim()
            val i = k.indexOf(' ')
            return if (i < 0) k to null else k.substring(0, i) to k.substring(i + 1).trim().ifEmpty { null }
        }
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

    private data class Entry(val uuid: String?, val login: String?, val password: String?)

    private val _mutex = Mutex()
    private var _session: Session? = null

    // the association (id, idKey), kept in memory only
    private var _association: Pair<String, String>? = null

    /** the password of the one entry matching 'key'; null if none matches; fails if several match */
    override suspend fun read(key: String): String? = locked { session ->
        val (url, login) = parseKey(key)
        one(key, logins(session, url, login))?.password
    }

    /**
     * stores 'value' as the password of the entry for 'key': updates the one matching entry (KeePassXC may ask the user),
     * or creates one, with the key's login, or else this source's identity, as its login
     */
    suspend fun write(key: String, value: String) = locked { session ->
        val (url, login) = parseKey(key)
        val existing = one(key, logins(session, url, login))
        request(session, "set-login", buildJsonObject {
            put("url", url)
            put("submitUrl", url)
            put("id", session.associationId)
            put("login", login ?: identity)
            put("password", value)
            existing?.uuid?.let { put("uuid", it) }
        })
        Unit
    }

    /** removes the entry for 'key', if there is one; KeePassXC asks the user, and moves it to its recycle bin */
    suspend fun remove(key: String) = locked { session ->
        val (url, login) = parseKey(key)
        val existing = one(key, logins(session, url, login)) ?: return@locked
        val uuid = existing.uuid ?: throw KeePassXCRequestException("KeePassXC did not say which entry is '$key', so it cannot be removed")
        val resp = request(session, "delete-entry", buildJsonObject { put("uuid", uuid) })
        if (resp?.get("success")?.jsonPrimitive?.contentOrNull == "false") throw KeePassXCDeclinedException("KeePassXC did not remove the entry for '$url'")
    }

    /** closes the connection to KeePassXC; the next request connects again (the association is kept) */
    fun close() {
        _session?.transport?.close()
        _session = null
    }

    override fun toString(): String = "SecretSourceKeePassXC($identity)"

    private suspend fun <T> locked(block: suspend (Session) -> T): T = _mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                block(session())
            } catch (e: KeePassXCNotAvailableException) {
                // a broken connection is not reused
                close()
                throw e
            }
        }
    }

    private fun one(key: String, entries: List<Entry>): Entry? = when (entries.size) {
        0 -> null
        1 -> entries[0]
        else -> throw KeePassXCRequestException("KeePassXC has ${entries.size} entries for '$key'; there must be one (add the login after a space to choose)")
    }

    private fun logins(session: Session, url: String, login: String?): List<Entry> {
        val resp = request(session, "get-logins", buildJsonObject {
            put("url", url)
            putJsonArray("keys") {
                addJsonObject {
                    put("id", session.associationId)
                    put("key", session.idKey)
                }
            }
        }, allowNotFound = true) ?: return emptyList()
        val entries = (resp["entries"] as? JsonArray ?: return emptyList()).map { it.jsonObject }.map {
            Entry(it["uuid"]?.jsonPrimitive?.contentOrNull, it["login"]?.jsonPrimitive?.contentOrNull, it["password"]?.jsonPrimitive?.contentOrNull)
        }
        return if (null == login) entries else entries.filter { it.login == login }
    }

    // --- protocol ---

    private fun session(): Session {
        _session?.let { return it }
        val transport = try {
            transportFactory()
        } catch (e: KeePassXCException) {
            throw e
        } catch (e: Exception) {
            throw KeePassXCNotAvailableException("KeePassXC is not running, or its browser integration is off: ${e.message}", e)
        }
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
            val serverPub = unb64(resp["publicKey"]?.jsonPrimitive?.contentOrNull ?: throw KeePassXCRequestException("KeePassXC sent no public key"))
            val session = Session(transport, clientId, pub, sec, serverPub)
            associate(session)
            _session = session
            return session
        } catch (t: Throwable) {
            transport.close()
            throw t
        }
    }

    /** uses this instance's association if KeePassXC still accepts it, else asks KeePassXC (and so the user) for a new one */
    private fun associate(session: Session) {
        _association?.let { (id, key) ->
            val ok = try {
                request(session, "test-associate", buildJsonObject {
                    put("id", id)
                    put("key", key)
                })
                true
            } catch (_: KeePassXCDeclinedException) {
                false
            } catch (_: KeePassXCRequestException) {
                false
            }
            if (ok) {
                session.associationId = id
                session.idKey = key
                return
            }
        }
        val idKey = b64(keyPair().first)
        val resp = request(session, "associate", buildJsonObject {
            put("key", b64(session.publicKey))
            put("idKey", idKey)
        }) ?: throw KeePassXCDeclinedException("KeePassXC did not allow this application to use it")
        val id = resp["id"]?.jsonPrimitive?.contentOrNull ?: throw KeePassXCRequestException("KeePassXC sent no association id")
        session.associationId = id
        session.idKey = idKey
        _association = id to idKey
    }

    /**
     * sends an encrypted request and returns the decrypted response.
     * Returns null if 'allowNotFound' and KeePassXC reports that no logins were found.
     */
    private fun request(session: Session, action: String, content: JsonObject, allowNotFound: Boolean = false): JsonObject? {
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
            when (code) {
                ERROR_NO_LOGINS_FOUND -> if (allowNotFound) return null
                ERROR_DATABASE_NOT_OPENED -> throw KeePassXCLockedException("The KeePassXC database is locked: unlock it in KeePassXC")
                ERROR_ACTION_CANCELLED_OR_DENIED, ERROR_ASSOCIATION_FAILED, ERROR_ACCESS_TO_ALL_ENTRIES_DENIED ->
                    throw KeePassXCDeclinedException("KeePassXC refused '$action': $error")
            }
            throw KeePassXCRequestException("KeePassXC '$action' failed: $error (code $code)")
        }
        val msg = resp["message"]?.jsonPrimitive?.contentOrNull ?: throw KeePassXCRequestException("KeePassXC '$action' sent no message")
        val respNonce = unb64(resp["nonce"]?.jsonPrimitive?.contentOrNull ?: throw KeePassXCRequestException("KeePassXC '$action' sent no nonce"))
        val plain = unbox(unb64(msg), respNonce, session.serverPublicKey, session.secretKey)
        val decrypted = try {
            Json.parseToJsonElement(plain.decodeToString()).jsonObject
        } catch (e: IllegalArgumentException) {
            throw KeePassXCRequestException("KeePassXC '$action' sent a message that is not JSON")
        }
        checkSuccess(decrypted, action)
        return decrypted
    }

    private fun checkSuccess(resp: JsonObject, action: String) {
        val success = resp["success"]?.jsonPrimitive?.contentOrNull
        // delete-entry answers success "false" when the user says no; the caller handles that
        if (success != null && success != "true" && action != "delete-entry") throw KeePassXCRequestException("KeePassXC '$action' did not succeed")
    }

    /** sends 'message' and returns the next message from KeePassXC for 'action' (other messages, e.g. notifications, are skipped) */
    private fun exchange(transport: KeePassXCTransport, message: JsonObject, action: String): JsonObject = try {
        transport.send(message.toString())
        var resp: JsonObject
        do {
            resp = Json.parseToJsonElement(transport.receive()).jsonObject
        } while (resp["action"]?.jsonPrimitive?.contentOrNull != action)
        resp
    } catch (e: KeePassXCException) {
        throw e
    } catch (e: IOException) {
        throw KeePassXCNotAvailableException("The connection to KeePassXC was lost: ${e.message}", e)
    } catch (e: IllegalStateException) {
        throw KeePassXCNotAvailableException("The connection to KeePassXC was lost: ${e.message}", e)
    }
}

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
                        ?: throw KeePassXCNotAvailableException("KeePassXC is not running, or its browser integration is off (no socket at ${paths.joinToString()})")
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
            if (n < 0) throw KeePassXCNotAvailableException("KeePassXC closed the connection")
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
            throw KeePassXCNotAvailableException("KeePassXC is not running, or its browser integration is off (no pipe at $path)", e)
        }
    }

    private val _splitter = JsonObjectSplitter()
    private val _buf = ByteArray(1024 * 1024)

    override fun send(message: String) = file.write(message.encodeToByteArray())

    override fun receive(): String {
        while (true) {
            _splitter.next()?.let { return it }
            val n = file.read(_buf)
            if (n < 0) throw KeePassXCNotAvailableException("KeePassXC closed the connection")
            // a message is sent in one write, so a UTF-8 sequence is not split across reads in practice
            _splitter.add(_buf.decodeToString(0, n))
        }
    }

    override fun close() = file.close()
}
