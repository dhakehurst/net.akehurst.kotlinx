# kotlinx-filesystem

File and directory access for Kotlin Multiplatform. Code works with directories and files through
one small API on every platform: the local disk on the JVM, files the user picks in the browser
(JS and Wasm), Korio virtual file systems, and resources bundled with the app.

```kotlin
val dir = UserFileSystemDefault.getDirectory("/home/me/project") ?: return
val notes = dir.getOrCreateDirectory("notes") ?: return
val todo = notes.getOrCreateFile("todo.txt") ?: return

todo.writeContent("buy milk")
println(todo.readContent())
```

## Contents

- [Modules](#modules)
- [Setup](#setup)
- [Handles](#handles)
- [Creating files and directories](#creating-files-and-directories)
- [File systems](#file-systems)
- [Dialogs](#dialogs)
- [Browser (JS and Wasm)](#browser-js-and-wasm)
- [Zip files](#zip-files)
- [API](#api)

## Modules

| Module | What it contains | Use it in |
|---|---|---|
| `kotlinx-filesystem-api` | The interfaces: `DirectoryHandle`, `FileHandle`, `UserFileSystem`, `FileAccessMode` | Libraries |
| `kotlinx-filesystem` | The implementations: `UserFileSystemDefault` for each platform, Korio and resources file systems | Apps |

A library that only passes handles around needs `kotlinx-filesystem-api`. The app adds
`kotlinx-filesystem` to get the implementations. `kotlinx-filesystem` includes the API module.

## Setup

The modules are published to Maven Central under the group `net.akehurst.kotlinx`.

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // in a library
                implementation("net.akehurst.kotlinx:kotlinx-filesystem-api:<version>")

                // or, in an app
                implementation("net.akehurst.kotlinx:kotlinx-filesystem:<version>")
            }
        }
    }
}
```

All operations that touch the file system are `suspend` functions.

## Handles

Everything in a file system is reached through a handle:

- `DirectoryHandle` for a directory
- `FileHandle` for a file

Both have a `name`, a `path`, an `absolutePath` and a `parent` (null at the root, or when it is not
known), and can check whether they still `exists()`.

```kotlin
val dir: DirectoryHandle = ...

for (entry in dir.listContent()) {
    when (entry) {
        is DirectoryHandle -> println("dir  ${entry.name}")
        is FileHandle -> println("file ${entry.name} (${entry.extension})")
    }
}

val readme: FileHandle? = dir.file("README.md")        // null if missing or not a file
val src: DirectoryHandle? = dir.directory("src")       // null if missing or not a directory
val any = dir.entry("build")                           // either kind, or null
```

Entries that are neither a file nor a directory, such as broken symbolic links, are left out of
`listContent()`, and `entry()` returns null for them.

File names:

| `name` | `extension` | `nameWithoutExtension` |
|---|---|---|
| `notes.txt` | `txt` | `notes` |
| `archive.tar.gz` | `gz` | `archive.tar` |
| `Makefile` | (empty) | `Makefile` |
| `.bashrc` | `bashrc` | (empty) |

`nameWithoutExtension` matches Kotlin's `File.nameWithoutExtension` (`kotlin.io`).

Reading and writing work on text (UTF-8) or on raw bytes:

```kotlin
val text: String? = file.readContent()
file.writeContent("new content")   // replaces the whole file

val bytes: ByteArray? = file.readBytes()   // not decoded; use this for binary files
file.writeBytes(bytes!!)                   // written as they are, replaces the whole file
```

`readContent` and `readBytes` return null if the file does not exist or cannot be read. Use the byte
versions for binary files such as images or databases, which decoding as text would corrupt.
`file.metadata()` gives the size and modification time where the platform provides them.

## Creating files and directories

| Call | Name is free | A file has the name | A directory has the name |
|---|---|---|---|
| `createFile(name)` | creates an empty file | returns it, content kept | `null` |
| `createDirectory(name)` | creates the directory | `null` | returns it |
| `getOrCreateFile(name)` | creates an empty file | returns it | `null` |
| `getOrCreateDirectory(name)` | creates the directory | `null` | returns it |

All four also return `null` if the entry cannot be created, for example because of permissions.
This is the same on every platform that has an implementation.

## File systems

| File system | What it gives access to | Platforms |
|---|---|---|
| `UserFileSystemDefault` | The local disk on the JVM; files and directories the user picks in the browser | JVM, JS, Wasm |
| `FileSystemKorio` | Any Korio `VfsFile` (local, memory, zip, ...) | All |
| `FileSystemFromVfs` | A Korio `VfsFile`, or a local path, as a root directory | All |
| `ResourcesFileSystem` | Resources bundled with the app | All |
| `UserHomeFileSystem(path)` | A directory under the user's home directory; access is kept inside it | All |

`UserFileSystemDefault` on macOS native is not implemented yet.

```kotlin
// JVM: a directory on disk
val project = UserFileSystemDefault.getDirectory("/home/me/project")

// resources bundled with the app
val config: String = ResourcesFileSystem.read("config.json")
val logo = ResourcesFileSystem.getFile("images/logo.svg")

// a directory under the user's home
val settings = UserHomeFileSystem(".myapp").root

// any Korio VfsFile
val handle = DirectoryHandleKorio(FileSystemKorio, null, someVfsFile)
```

## Dialogs

`UserFileSystem` can ask the user to choose a directory or file:

```kotlin
val dir = UserFileSystemDefault.selectDirectoryFromDialog("Choose a project")
val file = UserFileSystemDefault.selectExistingFileFromDialog("Open", current = dir)
val target = UserFileSystemDefault.selectNewFileFromDialog("Save as", parent = dir!!)
```

Each returns null if the user cancels.

- The dialog starts in `current` (or `parent`) where the platform supports it, but the user can
  choose anywhere.
- The returned handle's `parent` is the directory that contains it when that is known, otherwise
  null.
- On the JVM, `selectDirectoryFromDialog` uses the system dialog (`java.awt.FileDialog`) on macOS,
  and a Swing `JFileChooser` that only selects directories on other systems, because the AWT dialog
  cannot select a directory on Linux or Windows. The Swing dialogs use the system look and feel. The
  dialog is always shown on the AWT event thread.
- On the JVM, `selectExistingFileFromDialog` uses a Swing dialog by default, or the system dialog
  with `useNativeDialog = true`. `selectNewFileFromDialog` creates the chosen file if it does not exist.

## Browser (JS and Wasm)

In the browser, `UserFileSystemDefault` uses the
[File System Access API](https://developer.mozilla.org/docs/Web/API/File_System_API). The page can
only reach files and directories the user has picked in a dialog, and not every browser supports
these dialogs.

- `getDirectory(path)` cannot open a path directly in a browser. It shows the directory dialog
  instead.
- `accessMode` (`READ_ONLY` or `READ_WRITE`) is passed to the browser's directory dialog.
  Other dialogs, and the other platforms, do not use it.
- A file picked in the open-file dialog starts with read access only. `writeContent` asks the user
  for write permission the first time it is needed.
- The browser only shows that permission prompt when the write happens soon after a user action,
  such as a click. Write in response to the user's action, not later from a timer or background task.

## Zip files

Zip archives are read by a small zip reader in common code (no Korio). Archives are read-only and
are held in memory.

```kotlin
// a file on any file system; throws ZipException if it is not a zip archive
val zip: DirectoryHandle = dir.file("data.zip")!!.openAsZipDirectory()

// bytes from anywhere (download, SharePoint, a database, ...)
val root = ZipFileSystem.open(bytes, name = "data.zip", options = ZipOptions(maxEntrySize = 100L * 1024 * 1024))

for ((path, file) in root.walkFiles()) {           // all files, with paths relative to root
    if (!path.endsWith(".json")) continue
    val md = file.metadata()                        // size, lastModified, compressedSize, crc32
    if ((md.size ?: 0) > 10_000_000) continue       // check the size before reading
    val text = file.readContent()
}
```

- Errors throw `ZipException`, whose `reason` is one of `NOT_A_ZIP`, `CORRUPT_ARCHIVE`,
  `ENCRYPTED_ENTRY`, `UNSUPPORTED_COMPRESSION_METHOD`, `UNSUPPORTED_FEATURE` (multi-disk archives)
  or `ENTRY_TOO_LARGE`. Encrypted or unsupported entries are still listed; reading them throws.
- Writing or creating anything in an archive throws `ReadOnlyFileSystemException`.
- Supported: STORE and DEFLATE entries, ZIP64 (large archives and more than 65535 entries), UTF-8
  names (flag or Info-ZIP Unicode Path field) and CP437 names otherwise. CRC-32 is checked on read.
- DEFLATE uses `java.util.zip.Inflater` on the JVM and the browser's `DecompressionStream` on JS and Wasm.
- Names cannot escape the archive: `\` is read as `/`, and empty, `.` and `..` segments and drive
  letters are dropped (`../../a.txt` becomes `a.txt`). If two entries end up with the same path,
  the first is kept.
- Zip bombs: `metadata().size` is known before reading, `ZipOptions.maxEntrySize` limits reads,
  and inflating stops at the declared size, so an entry cannot produce more bytes than it claims.
- Memory: the whole archive is a `ByteArray`, so the practical limit is about 2 GB per archive
  (less in a browser), plus the full size of each entry while it is read.
- DOS timestamps have no time zone and are read as UTC; the extended timestamp field (UTC) is used
  when present.

## API

Package `net.akehurst.kotlinx.filesystem.api`.

| Type | Purpose |
|---|---|
| `FileSystemObjectHandle` | Common to files and directories: `name`, `path`, `absolutePath`, `parent`, `exists()` |
| `DirectoryHandle` | `listContent`, `entry`, `file`, `directory`, `createFile`, `createDirectory`, `getOrCreateFile`, `getOrCreateDirectory` |
| `FileHandle` | `extension`, `nameWithoutExtension`, `readContent`, `readBytes`, `writeContent`, `writeBytes`, `metadata`, `openAsZipDirectory` |
| `FileMetadata` | `size`, `lastModified`, `compressedSize`, `crc32` (each nullable) |
| `ZipException`, `ZipErrorReason` | Why a zip archive or entry could not be read |
| `ReadOnlyFileSystemException` | Thrown when writing to a read-only file system such as a zip archive |
| `walkFiles()`, `RelativeFile` | All files below a directory, with relative paths |
| `UserFileSystem` | Getting directories by path, dialogs, and the operations behind the handles |
| `FileAccessMode` | `READ_ONLY` or `READ_WRITE`, for the browser directory dialog |

The implementations (`UserFileSystemDefault`, `ZipFileSystem`, `ZipOptions`, `FileSystemKorio`, `FileSystemFromVfs`,
`ResourcesFileSystem`, `UserHomeFileSystem`) are in package `net.akehurst.kotlinx.filesystem`, in
the `kotlinx-filesystem` module.
