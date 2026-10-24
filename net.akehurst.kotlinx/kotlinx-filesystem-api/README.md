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

Reading and writing work on text:

```kotlin
val text: String? = file.readContent()
file.writeContent("new content")   // replaces the whole file
```

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
- On the JVM, `selectExistingFileFromDialog` uses a Swing dialog by default, or the system dialog
  with `useNativeDialog = true`.

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

A file can be opened as a zip archive. The result is a `DirectoryHandle` for the root of the
archive:

```kotlin
val zip = dir.file("data.zip")?.openAsZipDirectory() ?: return
val entry = zip.directory("sub")?.file("b.txt")?.readContent()
```

## API

Package `net.akehurst.kotlinx.filesystem.api`.

| Type | Purpose |
|---|---|
| `FileSystemObjectHandle` | Common to files and directories: `name`, `path`, `absolutePath`, `parent`, `exists()` |
| `DirectoryHandle` | `listContent`, `entry`, `file`, `directory`, `createFile`, `createDirectory`, `getOrCreateFile`, `getOrCreateDirectory` |
| `FileHandle` | `extension`, `nameWithoutExtension`, `readContent`, `writeContent`, `openAsZipDirectory` |
| `UserFileSystem` | Getting directories by path, dialogs, and the operations behind the handles |
| `FileAccessMode` | `READ_ONLY` or `READ_WRITE`, for the browser directory dialog |

The implementations (`UserFileSystemDefault`, `FileSystemKorio`, `FileSystemFromVfs`,
`ResourcesFileSystem`, `UserHomeFileSystem`) are in package `net.akehurst.kotlinx.filesystem`, in
the `kotlinx-filesystem` module.
