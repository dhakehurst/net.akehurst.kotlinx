# kotlinx-utils

Small multiplatform helpers used by the other kotlinx modules: glob patterns, observable values and
references, reference stores, unique identities, lazy values computed by a `suspend` function, and a
JSON serializer for `Any`.

```kotlin
val kotlinFiles = "src/**/*.kt".toRegexFromGlob('/')
kotlinFiles.matches("src/main/App.kt")   // true

val config by asyncLazy { loadConfig() } // loadConfig() is a suspend function
config()                                 // computed once, on first call

UniqueIdentityGenerator.GLOBAL.generate("node")   // "node_0", then "node_1", ...
```

## Contents

- [Setup](#setup)
- [Glob patterns](#glob-patterns)
- [Values](#values)
- [References](#references)
- [Reference stores](#reference-stores)
- [Unique identities](#unique-identities)
- [Async lazy values](#async-lazy-values)
- [Serializing `Any` to JSON](#serializing-any-to-json)
- [Other helpers](#other-helpers)
- [JS only](#js-only)
- [API](#api)

## Setup

The module is published to Maven Central under the group `net.akehurst.kotlinx`. It is built for
JVM, JS, Wasm (JS) and macOS (Arm64).

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("net.akehurst.kotlinx:kotlinx-utils:<version>")
            }
        }
    }
}
```

## Glob patterns

`String.toRegexFromGlob(separator, options)` turns a glob into a `Regex`. The `separator` is the
character between the parts of a name: `'/'` for file paths, `'.'` for Kotlin qualified names.

```kotlin
import net.akehurst.kotlinx.text.toRegexFromGlob

"com.example.**".toRegexFromGlob('.').matches("com.example.model.Person")   // true
"*.kt".toRegexFromGlob('/').matches("src/App.kt")                         // false: * stops at '/'
```

| Glob | Matches |
|---|---|
| `*` | Any characters except the separator, including none |
| `**` as a whole part (`**/`, `/**/`, `/**`, or the whole glob) | Zero or more whole parts: `**/b` matches `b` and `a/x/b` |
| `**` inside a part (e.g. `a**b`) | The same as `*` |
| `?` | Exactly one character, not the separator |
| `[abc]`, `[a-z]` | One of the characters, never the separator |
| `[!abc]` | One character not in the list, never the separator |
| `{a,b,c}` | Any one of the alternatives |
| `\` | Makes the next character literal, e.g. `\*`, `\{` or `\d` (a `d`, not a digit). A `\` at the end matches a `\` |

An unmatched `[`, `]` or `{` throws an exception. The whole name must match, as with `Regex.matches`.

## Values

A `Value<T>` wraps a value behind `get()`. `MutableValue<T>` adds `set()`.

| Class | Use |
|---|---|
| `ValueImmutable(value)` | A value that never changes |
| `MutableValueDefault(value)` | A value you can change |
| `ManagedValue(value, name, expectedType, onChanged)` | A value that checks its type and reports changes |

```kotlin
val title = ManagedValue<Any>("draft", "title", String::class) { old, new -> println("$old -> $new") }

title.set("final")   // prints "draft -> final"
title.set(42)        // throws IllegalArgumentException: not a String
```

`set(x)` and assigning `title.value = x` behave the same: the type is checked, and `onChanged` is
called. Both do nothing if the new value equals the current one. The starting value is checked too
(but doesn't call `onChanged`). `null` is always allowed. Two `ManagedValue` objects are only equal if
they are the same object.

## References

A `Reference<R, T>` holds a key (`reference`, of type `R`) for an object of type `T`, and the object
itself once it has been looked up (`resolved`). A reference can exist before the object it points to,
and be resolved later from a [reference store](#reference-stores).

There are three levels, each adding to the one before:

| Interface | Key | Resolved object | Use for |
|---|---|---|---|
| `Reference` | fixed | read only | Reading |
| `ResolvableReference` | fixed | can be set, e.g. by a store's `resolve` | Elements of collections of references |
| `MutableReference` | can change | can be set | A single-valued reference property |

| Class | Level | Use |
|---|---|---|
| `ReferenceImmutable(reference, resolved)` | `Reference` | A fixed reference, e.g. a snapshot |
| `ResolvableReferenceDefault(reference, resolved)` | `ResolvableReference` | A collection element |
| `ManagedResolvableReference(reference, name, expectedType, resolved)` | `ResolvableReference` | A collection element that checks the type of the resolved object |
| `MutableReferenceDefault(reference)` | `MutableReference` | A property you can change with `set(ref, value)` and `clear()` |
| `ManagedReference(reference, name, expectedType, onChanged)` | `MutableReference` | A property that checks the type of the resolved object and reports changes |

```kotlin
val owner = ManagedReference<String, Any>("bob", "owner", Person::class) { old, new ->
    println("${old.reference} -> ${new.reference}")
}

owner.set("alice", alice)   // prints "bob -> alice"
owner.clear()               // prints "alice -> null"
```

Assigning a different key, `owner.reference = "carol"`, is the same as `owner.set("carol", null)`:
the object resolved for the old key is dropped and `onChanged` is called. Resolve it again to get the
new object. Assigning the same key does nothing.

`onChanged` is given fixed snapshots (`ReferenceImmutable`) of the reference before and after the
change, never the `ManagedReference` itself, so a callback can put them in a collection.

All references are equal when their `reference` keys are equal, whatever their class: a
`ReferenceImmutable("k")` equals a `ManagedReference` with key `"k"`. Classes that implement
`Reference` should do the same, using `referenceEquals` and `referenceHashCode`.

Because equality and hash code follow the key, a reference whose key can change (a
`MutableReference`) must not be put in a collection or used as a map key: if its key changed, a
hash-based set could no longer find it, or could hold two references with the same key. Put
`ResolvableReference`s in collections instead: their key is fixed, and a store can still resolve them.

`ReferenceExt.mutable` casts a `Reference` to `MutableReference`, and `mutableOrNull` gives `null` if
it isn't one (e.g. for a `ResolvableReference`).

## Reference stores

A `ReferenceStore<R>` keeps objects by type and key, so references can be resolved later.

```kotlin
import net.akehurst.kotlinx.utils.ReferenceStoreExt.resolve

val store = ReferenceStoreByHashMap<String>()
store[Person::class, "alice"] = alice

val ref = MutableReferenceDefault<String, Person>("alice")
store.resolve(ref)   // returns alice, and sets ref.resolved to alice
```

Objects are stored under the exact class given: something stored as `Person::class` is not found
when looked up as `Any::class`.

`store.remove(Person::class, "alice")` removes an entry; setting a `null` value does the same.
Removing doesn't change references that were already resolved: they keep the old object in
`resolved`. So a non-null `resolved` means the object was found once, not that it is still in the store.

When `resolve` finds nothing for a key, it returns `null` and by default leaves `resolved` as it was.
That makes it safe to run again and again, and it never wipes a reference that was set by value to an
object that isn't in a store. To make references follow removals, pass `clearIfNotFound = true`:

```kotlin
store.resolve(ref, clearIfNotFound = true)   // not found: ref.resolved becomes null
```

A reference with no key is never changed by `resolve`. Both stores' `resolve` take `clearIfNotFound`.

`HierarchicalReferenceStoreByHashMap(parent, identity)` is a store with a parent, so stores form a
tree. `get` and `set` only use the store itself. To find an object anywhere in the tree, use
`HierarchicalReferenceStoreExt.resolve`, from any store in the tree:

```kotlin
import net.akehurst.kotlinx.utils.HierarchicalReferenceStoreExt.resolve

val model = HierarchicalReferenceStoreByHashMap<String>(null, "model")
val shapes = HierarchicalReferenceStoreByHashMap(model, "shapes")
shapes[Circle::class, "c1"] = c1

model.resolve(MutableReferenceDefault<String, Circle>("c1"))   // c1
model.resolve(MutableReferenceDefault<String, Shape>("c1"))    // c1, a Circle is a Shape
```

- **Each type belongs to one store.** The first store to set an object of a type becomes that type's
  owner. If a different store then sets an object of the same type, it throws `IllegalStateException`.
  A store stays the owner even after all its objects of that type are removed.
- **`resolve` looks in the owner of the reference's type first.** If the object isn't there, it looks
  for an object with the same key, stored under any other type, that is an instance of the
  reference's type. So a reference to `Shape` finds a `Circle`. If more than one object matches, it
  throws `IllegalStateException`.

## Unique identities

`UniqueIdentityGenerator` makes identities of the form `<commonPrefix><prefix>_<number>`, numbered
separately for each prefix and starting at 0. It is safe to use from several threads.

```kotlin
val ids = UniqueIdentityGenerator("doc.")
ids.generate()          // "doc.id_0"
ids.generate()          // "doc.id_1"
ids.generate("node")    // "doc.node_0"
```

`UniqueIdentityGenerator.GLOBAL` is a shared generator with no common prefix.

## Async lazy values

`asyncLazy` is like `lazy`, but the value is computed by a `suspend` function. Get the value by
calling the property, or with `await()`. The first call computes it; calls made at the same time wait
for that result rather than computing it again.

```kotlin
class Repository {
    val index by asyncLazy { buildIndex() }   // buildIndex() is a suspend function

    suspend fun find(name: String) = index()[name]
}
```

If the function throws, nothing is cached, and the next call tries again. `isInitialized()` says
whether the value has been computed. `AsyncLazy(initializer)` can also be used without delegation.

## Serializing `Any` to JSON

`AnySerializer` is a kotlinx-serialization `KSerializer<Any>` for JSON. Register it as the contextual
serializer for `Any`:

```kotlin
val json = Json {
    serializersModule = SerializersModule {
        contextual(Any::class, AnySerializer())
    }
}
```

It writes `null`, `String`, `Boolean`, `Int`, `Long`, `Float`, `Double`, `List` and `Map` as plain
JSON. Map keys are written with `toString()`. Any other object is passed to `customSerialize`, which
returns a class name and a `JsonObject`; the class name is written under the key `#class`. When
reading, an object with a `#class` key is passed to `customDeserialize`.

```kotlin
AnySerializer(
    customSerialize = { obj -> when (obj) {
        is Point -> "Point" to buildJsonObject { put("x", obj.x); put("y", obj.y) }
        else -> error("Can't serialize ${obj::class}")
    } },
    customDeserialize = { className, obj -> when (className) {
        "Point" -> Point(obj["x"]!!.jsonPrimitive.int, obj["y"]!!.jsonPrimitive.int)
        else -> error("Unknown class $className")
    } },
)
```

Values don't always come back with the same type. Whole numbers are read as `Long`, so an `Int`
comes back as a `Long`. Numbers with a `.` are read as `Double`, and numbers in exponent form with no
`.` (such as `1e-7`) are read as a `String`. A top-level `null` is read as `Unit`.

## Other helpers

| Declaration | Purpose |
|---|---|
| `Any.cast<T>()` | `this as T`, written as a call |
| `Indent(value, increment)` | Indentation for generated text: `indent.inc` is one level deeper, `toString()` gives the spaces |
| `Builder<T>` | An object with `build(): T`. `Builder.buildAction(ctor, init, action)` creates the builder, runs `init` on it, builds, and passes the result to `action` |
| `Identifiable<I>`, `NestedIdentifiable<I>` | Interfaces for objects with an `identity`, and for those nested in a parent with a `qualifiedIdentity` |
| `Factory`, `HierarchicalFactory` | Interfaces for factories with an identity, optionally nested in a parent factory |
| `Resolver`, `HierarchicalResolver` | Interfaces for resolvers with an identity, optionally nested in a parent resolver |

## JS only

These are in the JS source set, and are not available on Wasm.

| Declaration | Purpose |
|---|---|
| `objectJS { ... }` | Creates a plain JS object and fills it in with dynamic code: `objectJS { this.name = "x" }` |
| `objectJSTyped<T> { ... }` | The same, typed as an external interface `T` |
| `obj.setJsAny(key, value)` | Sets a property by name; returns `obj` |
| `obj.getJsAny<V>(key)` | Gets a property by name, typed as `V`. There is no check that the value is a `V` |
| `document.adoptCss(css)` | Adds a style sheet with the given CSS to the document. Throws if the CSS can't be parsed; `@import` rules are ignored |

## API

Package `net.akehurst.kotlinx.utils`, except `toRegexFromGlob`, which is in `net.akehurst.kotlinx.text`.

| File | Declarations |
|---|---|
| `RegexExtension.kt` | `String.toRegexFromGlob` |
| `Value.kt` | `Value`, `MutableValue`, `ValueImmutable`, `MutableValueAbstract`, `MutableValueDefault`, `ManagedValue`, `ValueExt` |
| `Reference.kt` | `Reference`, `MutableReference`, `ReferenceImmutable`, `MutableReferenceDefault`, `ManagedReference`, `ReferenceExt` |
| `ReferenceStore.kt` | `ReferenceStore`, `ReferenceStoreByHashMap`, `HierarchicalReferenceStore`, `HierarchicalReferenceStoreByHashMap`, `ReferenceStoreExt`, `HierarchicalReferenceStoreExt` |
| `UniqueIdentityGenerator.kt` | `UniqueIdentityGenerator` |
| `asynclazy.kt` | `asyncLazy`, `AsyncLazy` |
| `AnySerialiser.kt` | `AnySerializer` |
| `cast.kt`, `Indent.kt`, `Builder.kt` | `cast`, `Indent`, `Builder` |
| `identifiable.kt`, `factory.kt`, `resolver.kt` | `Identifiable`, `NestedIdentifiable`, `Factory`, `HierarchicalFactory`, `Resolver`, `HierarchicalResolver` |
| `utilsJs.kt` (JS) | `objectJS`, `objectJSTyped`, `setJsAny`, `getJsAny`, `adoptCss` |
