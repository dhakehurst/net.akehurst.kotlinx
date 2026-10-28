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

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary

/**
 * A SecretSource that reads generic credentials from the Windows Credential Manager (JVM on Windows only).
 * The secret for key K is the password of the generic credential whose target name is targetPrefix+K
 * (e.g. one added with `cmdkey /generic:K /user:U /pass:P`, or in Control Panel > Credential Manager > Windows Credentials).
 * On other platforms read fails.
 */
class SecretSourceWindowsCredentialManager(
    override val identity: String = "wincred",
    /** prepended to each key to give the credential target name */
    val targetPrefix: String = ""
) : SecretSource {

    internal companion object {
        const val CRED_TYPE_GENERIC = 1
        const val ERROR_NOT_FOUND = 1168

        @Suppress("FunctionName")
        internal interface Advapi32Cred : StdCallLibrary {
            fun CredReadW(targetName: WString, type: Int, flags: Int, credential: PointerByReference): Boolean
            fun CredFree(buffer: Pointer)
        }

        /** the start of CREDENTIALW, up to the blob */
        @Structure.FieldOrder("Flags", "Type", "TargetName", "Comment", "LastWrittenLow", "LastWrittenHigh", "CredentialBlobSize", "CredentialBlob")
        internal class Credential(p: Pointer) : Structure(p) {
            @JvmField var Flags: Int = 0
            @JvmField var Type: Int = 0
            @JvmField var TargetName: Pointer? = null
            @JvmField var Comment: Pointer? = null
            @JvmField var LastWrittenLow: Int = 0
            @JvmField var LastWrittenHigh: Int = 0
            @JvmField var CredentialBlobSize: Int = 0
            @JvmField var CredentialBlob: Pointer? = null

            init {
                read()
            }
        }

        val advapi32: Advapi32Cred by lazy { Native.load("Advapi32", Advapi32Cred::class.java) }
    }

    override suspend fun read(key: String): String? {
        check(Platform.isWindows()) { "The Windows Credential Manager is only available on Windows" }
        val target = targetPrefix + key
        val pcred = PointerByReference()
        if (!advapi32.CredReadW(WString(target), CRED_TYPE_GENERIC, 0, pcred)) {
            val err = Native.getLastError()
            if (err == ERROR_NOT_FOUND) return null
            error("Cannot read credential '$target' from the Windows Credential Manager (error $err)")
        }
        try {
            val cred = Credential(pcred.value)
            val blob = cred.CredentialBlob
            if (0 == cred.CredentialBlobSize || null == blob) return ""
            // cmdkey and the Control Panel store the password as UTF-16LE
            return String(blob.getByteArray(0, cred.CredentialBlobSize), Charsets.UTF_16LE)
        } finally {
            advapi32.CredFree(pcred.value)
        }
    }

    override fun toString(): String = "SecretSourceWindowsCredentialManager($identity, targetPrefix=$targetPrefix)"
}
