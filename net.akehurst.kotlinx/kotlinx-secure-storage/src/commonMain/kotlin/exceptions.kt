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

/** base class of the exceptions thrown by SecureStorageDatastore and Encryption */
open class SecureStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * the master password does not unlock the key file.
 * The datastore stays locked, so the next operation asks for the password again.
 */
class WrongMasterPasswordException(message: String = "The master password is wrong.", cause: Throwable? = null) :
    SecureStorageException(message, cause)

/** the master password provider returned null (the user cancelled), so the operation could not be done */
class MasterPasswordCancelledException(message: String = "No master password was given; the operation was cancelled.") :
    SecureStorageException(message)

/** the key file exists but cannot be read (unknown version, or not in a known format) */
class InvalidKeyFileException(message: String, cause: Throwable? = null) : SecureStorageException(message, cause)
