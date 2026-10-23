# kotlinx-reflect

Simple reflection for Kotlin Multiplatform: read and write properties, call functions, construct
instances, and query classes by name. It works the same way on JVM, JS, Wasm and Native, including
for classes in third-party libraries.

```kotlin
KotlinxReflectForModule.registerUsedClasses()   // once, at startup

val obj = KotlinxReflect.classForName("com.example.Person").reflect().construct("Bob")
obj.reflect().getProperty("name")          // "Bob"
obj.reflect().setProperty("age", 42)
obj.reflect().call("greet", "Hello")      // "Hello Bob"
```

## Contents

- [Setup](#setup)
- [Choosing classes: globs](#choosing-classes-globs)
- [Reflecting over third-party libraries](#reflecting-over-third-party-libraries)
- [What gets registered](#what-gets-registered)
- [How it works](#how-it-works)
- [API](#api)
- [Registering classes by hand](#registering-classes-by-hand)
- [Platform notes](#platform-notes)
- [Debugging: identical behaviour on every
  platform](#debugging-identical-behaviour-on-every-platform)
- [Migrating from the compiler-plugin versions](#migrating-from-the-compiler-plugin-versions)

## Setup

Kotlin's own reflection is only complete on JVM. On JS, Wasm and Native, kotlinx-reflect needs to
know about your classes in advance. The **kotlinx-reflect Gradle plugin** generates that information
for you, using [KSP](https://github.com/google/ksp).

**1. Apply the plugin** to a Kotlin multiplatform module, and list the classes you want to reflect
over:

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.4.0"
    id("net.akehurst.kotlinx.kotlinx-reflect-gradle-plugin") version "<version>"
}

kotlinxReflect {
    // classes available for reflection in this module (and its tests)
    forReflectionMain.set(listOf("com.example.model.*"))
    // extra classes for tests only, e.g. test fixtures declared in commonTest
    forReflectionTest.set(listOf("com.example.testmodel.*"))
}
```

The plugin also:
- adds the `net.akehurst.kotlinx:kotlinx-reflect` dependency
- applies and configures KSP; you don't need to add KSP yourself

**2. Register the classes** before using reflection, once, for example at startup:

```kotlin
import my_module_commonMain.KotlinxReflectForModule

KotlinxReflectForModule.registerUsedClasses()
```

The plugin generates `KotlinxReflectForModule` in the package `<module>_commonMain`, where
`<module>` is the Gradle project name with every character other than a letter or digit replaced by
`_`. For example, project `my-app` gets `my_app_commonMain.KotlinxReflectForModule`.

`registerUsedClasses()` can be called more than once, and from several threads: the registrations
run once, and other callers wait until they are complete. If a registration fails, the next call
tries again.

Tests have their own registry, `<module>_commonTest.KotlinxReflectForModule`. It first calls the
`commonMain` registry, so tests see the main classes exactly as the main code does. Then it
registers the classes declared in `commonTest` that match `forReflectionMain` or
`forReflectionTest`, and the other classes that match `forReflectionTest`:

```kotlin
import my_module_commonTest.KotlinxReflectForModule

@BeforeTest
fun setup() = KotlinxReflectForModule.registerUsedClasses()
```

Put classes from `commonMain` in `forReflectionMain`. The test compilation sees `commonMain` as a
library, so a `commonMain` class listed only in `forReflectionTest` is treated like a class from a
dependency: only its public members are registered, and `**` doesn't reach its sub-packages.

## Choosing classes: globs

Each entry in `forReflectionMain` and `forReflectionTest` is either a class name or a glob:

| Entry | Matches |
|---|---|
| `com.example.model.Person` | that class |
| `com.example.model.*` | every class in package `com.example.model` |
| `com.example.**` | every class in `com.example` and sub-packages, **this module only** |

Nested classes, including companion objects, are registered along with their outer class.

**The `**` limitation.** KSP can list the classes in a package you name, including packages in
dependencies, but it can't list a dependency's sub-packages. So for classes in dependencies, `**`
only reaches the glob's own package. List each package instead:

```kotlin
forReflectionMain.set(listOf("com.thirdparty.model.*", "com.thirdparty.model.events.*"))
```

The build prints a warning when a glob matches nothing.

## Reflecting over third-party libraries

List the library's packages in the module that uses the library, exactly like your own classes:

```kotlin
// in your module's build.gradle.kts; the library itself needs no plugin
kotlinxReflect {
    forReflectionMain.set(listOf("com.thirdparty.model.*"))
}
```

The registration code is generated into **your** module and uses only the library's public API, so
the library needs no plugin and doesn't need to know about kotlinx-reflect. Only public classes and
members of the library can be registered.

Registering the same class from several modules is harmless.

## What gets registered

For each matching class, the generated code registers:
- the class itself, with its enum values or `object` instance
- whether it is abstract (including sealed classes and interfaces) or an interface
- its direct supertypes
- its properties, in declaration order, with a setter for a `var` whose setter is public
- its constructors
- its member functions, including `suspend` functions

**Declared members only.** Only members the class declares or overrides are registered. Inherited
members are found through the registered supertypes.

**Visibility.** Public declarations are registered, and so are `internal` ones from your own module.
Private and protected members are not.

**Default arguments.** A constructor or function with default arguments is registered once for each
number of arguments it can be called with. `increment(by: Int = 1)` can be called with 0 or 1
arguments.

**Generics.** Type parameters are replaced by their bound, so `Box<T>` is used as `Box<Any?>` and
`NumberBox<N : Number>` as `NumberBox<Number>`.

**Not registered:**
- `vararg` functions and constructors
- functions and constructors whose type parameters have several bounds, or a bound that refers to
  the type parameter itself (e.g. `C : Comparable<C>`). The class's other members are still
  registered.
- classes and members deprecated with level `ERROR` or `HIDDEN`, and members whose parameter or
  property types are such classes
- enum entries that have their own body (they are anonymous subclasses)
- declarations that need an opt-in whose marker annotation isn't accessible (e.g. it is `internal`
  to a library)

**Opt-in.** Classes and members that need an opt-in (`@RequiresOptIn`) are registered. The
generated code opts in to their markers with `@file:OptIn`.

**Inner classes** are registered, but not their constructors, which need an instance of the outer
class.

**Overloads.** Every overload is registered with the types of its parameters. A call is matched by
name and number of arguments first. Only when several overloads take that many arguments are the
arguments' types checked, so the usual case costs nothing extra:
- the overload whose parameter types accept the arguments is called
- if none accepts them, or more than one does (e.g. `f(Any)` and `f(String)` called with a
  `String`), the call fails, rather than guessing
- `null` is accepted only by a nullable parameter

All platforms use the same code to choose, JVM included, so they choose the same overload. Types are
checked as far as they can be at runtime: type arguments are not checked (`List<String>` is checked
as `List`), a type parameter is checked against its bound, and function types and arrays are not
checked. On JS, numbers don't keep their Kotlin type, so `f(Int)` and `f(Double)` can't be told
apart for a whole number.

When a declaration with default values can be called with the same arguments as another overload
(e.g. `f(a: Int)` and `f(a: Int, b: Int = 0)` with one argument), the one that needs no default
values is used, as in Kotlin.

A function that has overloads, including inherited ones, is registered without its `KFunction`
reference.

Skipped declarations are logged at info level (`--info`).

## How it works

Every platform goes through the same `ClassReflection` / `ObjectReflection` API. Behind it, each
platform uses a backend:

| Platform | Backend |
|---|---|
| JVM | **kotlin-reflect**. Works for any class, registered or not, including Java classes. |
| JS | **Registry first**, then a fallback that looks members up by their JavaScript name. |
| Wasm, Native | **Registry only.** Classes must be registered. |

The **registry** is `KotlinxReflect`, the object where classes are registered. It's filled by the
code the Gradle plugin generates, or [by hand](#registering-classes-by-hand). When a registered
class is used, all platforms give the same answers and the same error messages.

**What the plugin generates:**
- **For `commonMain`:** KSP runs once on the common code and generates `KotlinxReflectForModule`.
- **For tests:** Kotlin doesn't compile `commonTest` as a separate step, so the plugin writes a
  small fixed `KotlinxReflectForModule` into `commonTest`. It calls the `commonMain` registry, then
  the registrations that KSP generates once per test target for the classes the `commonMain`
  registry doesn't have.

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
| `isAbstract`, `isInterface`, `isEnum`, `isObject` | Class kind; sealed classes are abstract |
| `allPropertyNames` | Property names in declaration order, inherited ones first |
| `allMemberFunctions` | Member functions (`KFunction`s), where available |
| `construct(vararg args)` | Constructs with that many arguments; an `object` returns itself |
| `isSupertypeOf(subtype)` | True if `subtype` is this class or inherits from it |
| `getProperty(self, name)`, `setProperty(self, name, value)` | Read or write a property |
| `isPropertyMutable(name)` | True only for a `var` with a public setter |
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
Method 'load' on class 'Person' is a suspend function, use 'callSuspend' instead of 'call'
No constructor with 0 parameter(s) found for class 'Person'
No constructor of class 'Person' accepts arguments (Boolean)
No overload of method 'greet' on class 'Person' accepts arguments (Int)
Several overloads of method 'show' on class 'Person' accept arguments (String)
```

An exception thrown by the code you call (a constructor, getter or function) is passed through
unchanged.

### Other functions

- `KotlinxReflect.classForName(qualifiedName)` finds a registered class.
- `KotlinxReflect.qualifiedNameForClass(kclass)` returns the name a class was registered with.
- `proxyFor<T>(interfaceClass) { handler, proxy, callable, methodName, args -> ... }` creates a
  dynamic proxy for an interface. **JVM only.**
- `kfunction.isSuspend()` tells you whether a function is `suspend`. On non-JVM platforms the
  function must be registered.

## Registering classes by hand

You don't need this if you use the Gradle plugin. It's useful when you can't use the plugin, or for
a class the plugin can't handle.

Register the class first, then its details:

```kotlin
class Person(var name: String, val age: Int) : Named {
    fun greet(greeting: String) = "$greeting $name"
    suspend fun load(id: Int): String = TODO()
}

with(KotlinxReflect) {
    registerClass("com.example.Person", Person::class)
    registerClassInfo(
        Person::class, isAbstract = false, isInterface = false,
        supertypes = listOf(Named::class, Any::class)
    )

    // properties: a getter, and a setter for a mutable property
    registerProperty(
        Person::class, "name",
        { self -> (self as Person).name },
        { self, v -> (self as Person).name = v as String }
    )
    registerProperty(Person::class, "age", { self -> (self as Person).age })

    registerConstructor(Person::class, 2) { args -> Person(args[0] as String, args[1] as Int) }

    registerFunction(
        Person::class, "greet", 1,
        { self, args -> (self as Person).greet(args[0] as String) },
        Person::greet
    )
    registerSuspendFunction(
        Person::class, "load", 1,
        { self, args -> (self as Person).load(args[0] as Int) },
        Person::load
    )
}
```

**Two ways to register a property:**
- **With lambdas,** `registerProperty(cls, name, getter, setter)` as above. This works for every
  kind of class. It's what the generated code uses.
- **With a property reference,** `registerProperty(Person::class, Person::name)`. This is shorter,
  but doesn't work for `object` declarations: there, `MyObject::prop` is a reference bound to the
  object, not a `KProperty1`. It's also awkward for generic classes.

**Other points:**
- **What to register for each class:** only the members it declares or overrides. Inherited ones are
  found through the registered supertypes.
- **Enums:** pass the values function, for example `registerClass(qn, Colour::class, {
  Colour.values() as Array<Enum<*>> })`.
- **Objects:** pass the instance, for example `registerClass(qn, MyObject::class, null, MyObject)`.
- **Overloads with the same number of parameters:** register each with its parameter types instead
  of the number of parameters, so that the right one can be chosen:
  ```kotlin
  registerConstructor(Person::class, listOf(ParameterType(String::class, false))) { args ->
      Person(args[0] as String)
  }
  registerFunction(
      Person::class, "greet", listOf(ParameterType(Int::class, isNullable = false)),
      { self, args -> (self as Person).greet(args[0] as Int) }
  )
  ```
  An overload registered by number of parameters only accepts any arguments.
- **Function references** (the last argument of `registerFunction`) are optional. They're used for
  `allMemberFunctions` and `isSuspend()`.
- **Registering twice is safe.** Calling `registerClass` again for the same class keeps what is
  already registered.

See [`testRegistrations.kt`](src/commonTest/kotlin/kotlinx/testRegistrations.kt) for a complete
example.

## Platform notes

### JVM

- Uses kotlin-reflect, so registration isn't needed.
- Property order comes from the class's Kotlin metadata, so it's declaration order, as on other
  platforms. Java classes keep kotlin-reflect's order.
- Properties inherited from `kotlin.*` and `java.*` types (such as `Enum.name`) aren't listed,
  matching the registry.
- In Java classes, `getX()` / `setX(...)` methods are treated as a property `x`. In Kotlin classes
  they are ordinary functions, as on other platforms.
- Functions that take inline (value) classes are supported.

### JS

Anything registered is used first. Anything not registered (a class, property, function or
constructor) falls back to JavaScript:

- **Properties** are found through the accessor methods Kotlin/JS generates for every property. This
  also works for classes that aren't exported. `val` and `var` are told apart. Properties inherited
  from `Enum` and `Throwable` (such as `name` and `message`) aren't listed, as on other platforms.
  For a class with registered properties, only those are listed. Without them, the fallback may
  also list `internal` properties, which JavaScript can't tell apart.
- **Functions** are called by name. That needs unmangled names, so export the class with the
  **exportPublic** plugin (`net.akehurst.kotlin.gradle.plugin.exportPublic`) or `@JsExport`.
- **Constructors:** if a class has registered constructors, only those are used. Otherwise
  `Reflect.construct` is called.
- **Supertypes:** only the superclass chain is visible. Interfaces are not.
- **Enums** are detected automatically.
- **Objects** are detected automatically, but their instance is only available when registered;
  `construct()` on an unregistered `object` fails rather than creating a second instance.
- **Abstract classes:** the fallback guesses `isAbstract` from the class name ending in `Abstract`.
- **Not available through the fallback:** `suspend` functions, `allMemberFunctions` and
  `qualifiedName` need registration.

Registered classes need neither exportPublic nor `@JsExport`.

### Wasm and Native

- Only registered classes can be reflected.
- `proxyFor` is not available.

## Debugging: identical behaviour on every platform

Set `KotlinxReflect.forceRegistry = true` to use **only** the registry, on JVM and JS too. Every
platform then behaves exactly like Wasm and Native. This is useful for reproducing a JS or Wasm
problem on JVM, or for checking that everything you need is registered.

On JVM it can also be set with a system property:

```
-Dkotlinx.reflect.forceRegistry=true
```

This module's own tests run on every platform. On JVM they run twice: `jvmTest` uses kotlin-reflect,
and `jvmTestRegistry` forces the registry.

```bash
./gradlew :kotlinx-reflect:allTests
```

The [integration tests](../../kotlinx-reflect-integration-test) apply the Gradle plugin to a module
that reflects over a separate module with no plugins, standing in for a third-party library. On JVM,
`test_BackendsAgree` checks that kotlin-reflect and the generated registrations give the same
answers for every test class.

## Migrating from the compiler-plugin versions

Up to 2.3.x, the Gradle plugin was a Kotlin compiler plugin. From 2.4.0 it generates source code
with KSP instead.

- **Registration is now explicit (breaking change).** The old plugin rewrote
  `KotlinxReflect.classForName(...)` calls so that classes registered themselves. Now call
  `KotlinxReflectForModule.registerUsedClasses()` once before using reflection. Without it,
  `classForName` fails with *"Cannot find class …, is the class registered with KotlinxReflect and
  did you remember to call 'KotlinxReflectForModule.registerUsedClasses()'?"*.
- **Apply the plugin where the classes are used, not where they are declared.** It now registers
  classes from dependencies, so the plugin and its `kotlinxReflect { }` configuration belong in the
  module that does the reflecting.
- **Use globs.** Write `com.example.model.*` for the classes of a package; a name without `*` is
  treated as a single class.
- **Unchanged:** the plugin id and the `kotlinxReflect { forReflectionMain / forReflectionTest }`
  configuration.
- **No project-wide KSP options.** If your build also uses KSP for other processors, don't set the
  `kotlinxReflect.*` options with `ksp { arg(...) }`. The plugin sets them per KSP task, and
  project-wide values would override them.
- **KSP version.** The plugin applies KSP 2.3.12. To use a newer version, apply
  `com.google.devtools.ksp` with that version in your build as well.
