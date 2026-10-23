# kotlinx-reflect

Simple reflection for Kotlin Multiplatform: read and write properties, call functions, construct instances, and query classes by name. It works the same way on JVM, JS, Wasm and Native.

```kotlin
val obj = KotlinxReflect.classForName("com.example.Person").reflect().construct("Bob")
obj.reflect().getProperty("name")          // "Bob"
obj.reflect().setProperty("age", 42)
obj.reflect().call("greet", "Hello")      // "Hello Bob"
```

## Contents

- [Setup](#setup)
- [How it works](#how-it-works)
- [API](#api)
- [Registering classes by hand](#registering-classes-by-hand)
- [Platform notes](#platform-notes)
- [Debugging: identical behaviour on every platform](#debugging-identical-behaviour-on-every-platform)

## Setup

Kotlin's own reflection is only complete on JVM. On JS, Wasm and Native, kotlinx-reflect needs to know about your classes in advance. The **kotlinx-reflect Gradle plugin** does this for you: it finds the classes you list and registers them when your module first uses reflection.

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.4.0"
    id("net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin") version "<version>"
}

kotlinxReflect {
    // packages whose classes should be available for reflection
    forReflectionMain.set(listOf("com.example.model"))
    // extra packages for test compilations only
    forReflectionTest.set(listOf("com.example.testmodel"))
}
```

The plugin adds the `net.akehurst.kotlinx:kotlinx-reflect` dependency itself.

> **Current status:** the plugin registers each class with its enum values and `object` instance. It does not yet register properties, functions, constructors or supertypes. Until it does:
> - **JVM and JS** work without them.
> - **Wasm and Native** need them [registered by hand](#registering-classes-by-hand).

For JS, also apply the **exportPublic** plugin (`net.akehurst.kotlin.gradle.plugin.exportPublic`) to the module that declares the classes. It stops their names being mangled, which the JS name-based lookup relies on (see [Platform notes](#platform-notes)).

## How it works

Every platform goes through the same `ClassReflection` / `ObjectReflection` API. Behind it, each platform uses a backend:

| Platform | Backend |
|---|---|
| JVM | **kotlin-reflect**. Works for any class, registered or not, including Java classes. |
| JS | **Registry first**, then a fallback that looks members up by their JavaScript name. |
| Wasm, Native | **Registry only.** Classes must be registered. |

The **registry** is `KotlinxReflect`, the object where classes are registered. It's normally filled by the code the Gradle plugin generates. When a registered class is used, all platforms give the same answers and the same error messages.

## API

Get a reflection object from a class or an instance:

```kotlin
import net.akehurst.kotlinx.reflect.reflect
import net.akehurst.kotlinx.reflect.KotlinxReflect

val cls = KotlinxReflect.classForName("com.example.Person")   // KClass<*>
val cr  = cls.reflect()                                        // ClassReflection
val or  = person.reflect()                                     // ObjectReflection
```

### `ClassReflection`

| Member | Description |
|---|---|
| `qualifiedName` | Fully qualified class name |
| `isAbstract`, `isInterface`, `isEnum`, `isObject` | Class kind |
| `allPropertyNames` | Property names, inherited ones first |
| `allMemberFunctions` | Member functions (`KFunction`s), where available |
| `construct(vararg args)` | Calls the constructor that takes that many arguments. For an `object`, returns its instance. |
| `isSupertypeOf(subtype)` | True if `subtype` is this class or inherits from it |
| `getProperty(self, name)` / `setProperty(self, name, value)` | Read or write a property of an instance |
| `isPropertyMutable(name)` | True for a `var`. False for a `val` or an unknown name. |
| `call(self, name, vararg args)` | Calls a member function |
| `callSuspend(self, name, vararg args)` | Calls a `suspend` member function |
| `enumValues<E>()`, `enumValueOf<E>(name)` | Enum entries |

### `ObjectReflection`

Has the same operations, applied to one instance:
- `getProperty(name)` and `setProperty(name, value)`
- `isPropertyMutable(name)`
- `call(name, vararg args)` and `callSuspend(name, vararg args)`
- `allPropertyNames`, `construct(...)` and `isSupertypeOf(...)`
- `isProxy`

### Errors

Failures throw `IllegalStateException` with the same message on every platform, for example:

```
Property 'nope' not found on class 'Person'
Property 'name' on class 'Person' is not mutable
Method 'greet' with 2 parameter(s) not found on class 'Person'
No constructor with 0 parameter(s) found for class 'Person'
```

An exception thrown by the code you call (a constructor, getter or function) is passed through unchanged.

### Other functions

- `KotlinxReflect.classForName(qualifiedName)` finds a registered class.
- `KotlinxReflect.qualifiedNameForClass(kclass)` returns the name a class was registered with.
- `proxyFor<T>(interfaceClass) { handler, proxy, callable, methodName, args -> ... }` creates a dynamic proxy for an interface. **JVM only.**
- `kfunction.isSuspend()` tells you whether a function is `suspend`. On non-JVM platforms the function must be registered.

## Registering classes by hand

You don't need this if you use the Gradle plugin. It's useful when:
- you can't use the plugin
- a class lives in a module that isn't listed in `forReflectionMain`
- you're writing tests

Register the class first, then its details:

```kotlin
class Person(var name: String, val age: Int) : Named {
    fun greet(greeting: String) = "$greeting $name"
    suspend fun load(id: Int): String = TODO()
}

with(KotlinxReflect) {
    registerClass("com.example.Person", Person::class)
    registerClassInfo(Person::class, isAbstract = false, isInterface = false, supertypes = listOf(Named::class, Any::class))

    registerProperty(Person::class, Person::name)
    registerProperty(Person::class, Person::age)

    registerConstructor(Person::class, 2) { args -> Person(args[0] as String, args[1] as Int) }

    registerFunction(Person::class, "greet", 1, { self, args -> (self as Person).greet(args[0] as String) }, Person::greet)
    registerSuspendFunction(Person::class, "load", 1, { self, args -> (self as Person).load(args[0] as Int) }, Person::load)
}
```

- **What to register for each class:** only the members it declares or overrides. Inherited ones are found through the registered supertypes.
- **Enums:** pass the values function, for example `registerClass(qn, Colour::class, Colour::values as EnumValuesFunction)`.
- **Objects:** pass the instance, for example `registerClass(qn, MyObject::class, null, MyObject)`.
- **Registering twice is safe.** Calling `registerClass` again for the same class keeps what is already registered.
- **Overloads:** functions and constructors are matched by name and number of arguments. Overloads with the same number of parameters aren't supported.

See [`testRegistrations.kt`](src/commonTest/kotlin/kotlinx/testRegistrations.kt) for a complete example.

## Platform notes

### JVM

- Uses kotlin-reflect, so registration isn't needed.
- Java-style `getX()` / `setX(...)` methods are treated as a property `x`.
- Functions that take inline (value) classes are supported.

### JS

Anything registered is used first. Anything not registered (a class, property, function or constructor) falls back to JavaScript:

- **Properties** are found through the accessor methods Kotlin/JS generates for every property. This also works for classes that aren't exported. `val` and `var` are told apart.
- **Functions** are called by name. That needs unmangled names, so export the class with the **exportPublic** plugin or `@JsExport`.
- **Constructors:** if a class has registered constructors, only those are used. Otherwise `Reflect.construct` is called.
- **Supertypes:** only the superclass chain is visible. Interfaces are not.
- **Enums** are detected automatically.
- **Abstract classes:** the fallback guesses `isAbstract` from the class name ending in `Abstract`.
- **Not available through the fallback:** `suspend` functions, `allMemberFunctions` and `qualifiedName` need registration.

### Wasm and Native

- Only registered classes can be reflected.
- `proxyFor` is not available.

## Debugging: identical behaviour on every platform

Set `KotlinxReflect.forceRegistry = true` to use **only** the registry, on JVM and JS too. Every platform then behaves exactly like Wasm and Native. This is useful for reproducing a JS or Wasm problem on JVM, or for checking that everything you need is registered.

On JVM it can also be set with a system property:

```
-Dkotlinx.reflect.forceRegistry=true
```

This module's own tests run on every platform. On JVM they run twice: `jvmTest` uses kotlin-reflect, and `jvmTestRegistry` forces the registry.

```bash
./gradlew :kotlinx-reflect:allTests
```
