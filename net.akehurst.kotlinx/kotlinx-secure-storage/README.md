# kotlinx-secure-storage

Keeping secrets for Kotlin Multiplatform apps: an encrypted key/value file unlocked with a master
password, and a common `SecretSource` interface for reading secrets from wherever they are kept
(environment variables, the user, the Windows Credential Manager, KeePassXC, or the encrypted file).

```kotlin
val store = SecureStorageDatastore(
    secureStorageFile = dir.getOrCreateFile("secrets.txt")!!,
    encrypt = true,
    masterKeyFile = dir.getOrCreateFile("secrets.key")!!,
    masterPassword = { isNew -> askUserForPassword(confirm = isNew) },   // null if the user cancels
)

store.writeString("api-token", "s3cr3t")
store.readString("api-token")   // "s3cr3t"
```

## Contents

- [Setup](#setup)
- [Secure storage file](#secure-storage-file)
- [Encryption and the key file](#encryption-and-the-key-file)
- [Secret sources](#secret-sources)
- [API](#api)

## Setup

The module is published to Maven Central under the group `net.akehurst.kotlinx`. It uses
`kotlinx-filesystem-api` for files, so the app also needs `kotlinx-filesystem` (or another
implementation of the file handles).

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("net.akehurst.kotlinx:kotlinx-secure-storage:<version>")
                implementation("net.akehurst.kotlinx:kotlinx-filesystem:<version>")
            }
        }
    }
}
```

Everything is in package `net.akehurst.kotlinx.secure.storage`.

## Secure storage file

`SecureStorageDatastore` keeps entries in a text file, one `key=value` line per entry. With
`encrypt = true` each value is encrypted; keys are always stored in plain text.

| Operation | Notes |
|---|---|
| `write(key, value)`, `writeString(key, value)` | Adds or replaces an entry |
| `read(key)`, `readString(key)` | The value, or null if there is no entry. `readString` gives a non-`String` value by its `toString()` |
| `keys()` | All keys. No password is needed, because keys are not encrypted |
| `remove(key)` (or `delete`) | Removes an entry |
| `clear()` | Removes all entries; the key file and master password are kept |
| `reset()` | For a forgotten master password: deletes the key file and the storage file (or empties them where they cannot be deleted). All values are lost |
| `isUnlocked` | Whether the master password has been given |

- **The master password is asked for only when it is first needed**, by calling the
  `masterPassword` provider, a `suspend (isNew: Boolean) -> String?`. `isNew` is true when there is no
  key file yet, so the app can ask for a new password twice. Returning null cancels the operation with
  `MasterPasswordCancelledException`.
- A wrong password throws `WrongMasterPasswordException`. The store stays locked, and the next
  operation that needs the key calls the provider again.
- `String` values need nothing else. Other values are written as JSON and need a contextual serializer
  for `Any` in `jsonSerializersModule`, such as `AnySerializer` from `kotlinx-utils`.
- All operations are safe to call concurrently. The file is loaded before the first read, write or
  remove, so existing entries are never lost. Values may contain the separator.
- On the JVM the storage file and key file are made readable and writable by their owner only
  (POSIX 600 where the file system supports it). They are created empty and restricted before anything
  is written to them.

## Encryption and the key file

Values are encrypted with AES-GCM using a random key. That key is kept in the key file, encrypted with
a key derived from the master password (PBKDF2-SHA256 with a random 16-byte salt, 600,000 iterations
for new key files). The key file is JSON:

```json
{"version":1,"kdf":"PBKDF2-SHA256","iterations":600000,"salt":"<base64>","key":"<base64 wrapped key>"}
```

Key files written by older versions (a bare base64 key, derived with a fixed salt) still load, and are
rewritten in the current format with a new random salt the first time they are unlocked. A key file in
an unknown format throws `InvalidKeyFileException`.

`Encryption` can also be used on its own: `createKey(keyFile, password)`, `loadKey(keyFile, password)`,
`encryptString`, `decryptString` and `unload()`.

## Secret sources

A `SecretSource` gives the secret stored under a key, or null if there is none. Register sources in a
`SecretSourceRegistry` (`SecretSourceRegistryDefault.GLOBAL` is a shared one) and look them up by
`identity`, so configuration can name where each secret comes from.

```kotlin
val registry = SecretSourceRegistryDefault.GLOBAL
registry.register(SecretSourceEnvironment(prefix = "MYAPP_"))
registry.register(SecretSourceSecureStorage("file", store))

val token = registry.getOrNull("env")?.read("API_TOKEN")   // reads MYAPP_API_TOKEN
```

| Source | Default identity | Reads from | Platforms |
|---|---|---|---|
| `SecretSourceInMemory(identity, secrets)` | (given) | A map in memory, with `set` and `remove`. For tests, or secrets the user typed in | All |
| `SecretSourceEnvironment(identity, prefix)` | `env` | The environment variable `prefix + key`. Always null in a browser | All (JVM and Node.js have an environment) |
| `SecretSourceSecureStorage(identity, storage)` | (given) | Any `SecureStorage`, such as a `SecureStorageDatastore` | All |
| `SecretSourcePrompt(identity, prompt)` | `prompt` | Asks the user through the app's own UI, and remembers each answer in memory until `forget()` | All |
| `SecretSourceWindowsCredentialManager(identity, targetPrefix)` | `wincred` | The password of the generic credential named `targetPrefix + key` (e.g. added with `cmdkey /generic:K /user:U /pass:P`) | JVM on Windows |
| `SecretSourceKeePassXC(identity, triggerUnlock)` | `keepassxc` | A running KeePassXC, through its browser-integration protocol. The key is an entry URL, optionally followed by a space and a login; the secret is the matching entry's password. Can also write and remove | JVM |

### KeePassXC

```kotlin
val keepass = SecretSourceKeePassXC()
keepass.read("https://service.example.com")           // the password of the one entry for that URL
keepass.read("https://service.example.com alice")     // only entries with the login "alice"
keepass.write("https://service.example.com alice", "new password")
keepass.remove("https://service.example.com alice")
```

- Browser integration must be enabled in KeePassXC (Settings > Browser Integration). KeePassXC does
  the unlocking (`triggerUnlock = true`, the default, asks it to show its unlock dialog); this source
  never sees the database password.
- A key is the URL of an entry, optionally followed by one space and a login. With a login, only
  entries with that login match.
- `read` gives null if no entry matches, and fails if several match.
- `write(key, value)` updates the one matching entry (KeePassXC may ask the user), or creates one whose
  login is the key's login, or else the source's identity.
- `remove(key)` deletes the matching entry (KeePassXC asks the user, and moves it to its recycle bin).
  It does nothing if there is none.
- The first time, KeePassXC asks the user to allow this client. That association is kept in memory
  only, never written anywhere, and reused while KeePassXC accepts it, so the user allows it once per
  instance of the source.
- `close()` closes the connection; the next request connects again and keeps the association. A
  connection that was lost is closed and reconnected on the next request.
- Failures throw a subclass of the sealed `KeePassXCException`, each with a message meant for the user:

| Exception | When |
|---|---|
| `KeePassXCNotAvailableException` | KeePassXC is not running, browser integration is off, or the connection was lost |
| `KeePassXCLockedException` | The database is locked and the user did not unlock it |
| `KeePassXCDeclinedException` | The user did not allow this application, or refused a request such as an update or removal |
| `KeePassXCRequestException` | KeePassXC answered unexpectedly, e.g. several entries match one key |

`KeePassXCTransport` is the connection to KeePassXC's browser-integration socket: a Unix domain socket
on Linux and macOS, or a named pipe on Windows. `KeePassXCTransport.connect()` finds and connects to
the running KeePassXC.

For browser apps, `SecretSourcePrompt` with a login form (a username and a password field, on the
app's URL) lets a password manager such as KeePassXC-Browser fill the secret in.

## API

| Type | Purpose |
|---|---|
| `SecureStorage` | `write(key, value)` and `read(key)` |
| `SecureStorageDatastore` | A `SecureStorage` in a file, optionally encrypted |
| `MasterPasswordProvider` | `suspend (isNew: Boolean) -> String?` |
| `Encryption` | The AES-GCM key, and the key file that holds it |
| `SecureStorageException` | Base of `WrongMasterPasswordException`, `MasterPasswordCancelledException` and `InvalidKeyFileException` |
| `SecretSource`, `SecretSourceRegistry`, `SecretSourceRegistryDefault` | Reading secrets, and finding sources by identity |
| `SecretSourceInMemory`, `SecretSourceEnvironment`, `SecretSourceSecureStorage`, `SecretSourcePrompt` | Sources on all platforms |
| `SecretSourceWindowsCredentialManager`, `SecretSourceKeePassXC` | JVM sources |
| `KeePassXCException` and its subclasses, `KeePassXCTransport` | KeePassXC failures, and the connection to it (JVM) |
