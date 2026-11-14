[![Maven Central](https://img.shields.io/maven-central/v/net.akehurst.kotlinx/kotlinx-collections.svg)](https://search.maven.org/artifact/net.akehurst.kotlinx/kotlinx-collections)
![Kotlin Version](https://img.shields.io/badge/dynamic/regex?url=https%3A%2F%2Fraw.githubusercontent.com%2Fdhakehurst%2Fnet.akehurst.kotlin.compose%2Frefs%2Fheads%2Fmain%2Fnet.akehurst.kotlin.compose%2Fgradle%2Flibs.versions.toml&search=kotlin%20%3D%20%22(%5B%5E%22%5D%2B)%22&replace=%241&label=Kotlin&color=green&link=https%3A%2F%2Fkotlinlang.org%2F)


# net.akehurst.kotlinx

A collection of useful multiplatform extensions to the kotlin stdlib

| Module | What it contains |
|---|---|
| [kotlinx-collections](net.akehurst.kotlinx/kotlinx-collections/README.md) | Ordered sets, managed and derived collections, stacks, queues, binary heaps, lazy and cached maps, graph and tree helpers |
| [kotlinx-filesystem](net.akehurst.kotlinx/kotlinx-filesystem-api/README.md) | File and directory handles on every platform (disk, browser, resources, Korio), file dialogs, zip archives |
| [kotlinx-logging](net.akehurst.kotlinx/kotlinx-logging-api/README.md) | A logging API with console and log4j back ends, and bridges for other logging libraries |
| [kotlinx-reflect](net.akehurst.kotlinx/kotlinx-reflect/README.md) | Multiplatform reflection, with a [Gradle plugin](net.akehurst.kotlinx/kotlinx-reflect-gradle-plugin/README.md) that generates the registrations using KSP |
| [kotlinx-secure-storage](net.akehurst.kotlinx/kotlinx-secure-storage/README.md) | An encrypted key/value file unlocked with a master password, and sources for reading secrets |
| [kotlinx-utils](net.akehurst.kotlinx/kotlinx-utils/README.md) | Globs, managed and derived values, references and reference stores, unique ids, async lazy, a JSON serializer for `Any` |

The libraries are published to Maven Central under the group `net.akehurst.kotlinx`. The multiplatform
ones are built for JVM, JS and Wasm (JS); the macOS target is switched off for now, because it needs
Xcode to build. 
