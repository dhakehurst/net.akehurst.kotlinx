package net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection

import kotlin.jvm.JvmInline

// One group of classes per kind of declaration the KSP processor must handle.
// This module applies no plugins; the consuming module registers these classes.

// ---- suspend functions ----
class SuspendThing(val base: Int) {
    suspend fun add(x: Int): Int = base + x
    suspend fun hello(): String = "hello"
    fun notSuspend(): String = "plain"
}

// ---- value (inline) classes ----
@JvmInline
value class Id(val value: String)

class Holder(val id: Id, var other: Id? = null) {
    fun rename(newId: Id): Holder = Holder(newId, other)
    fun idValue(): String = id.value
}

// ---- generics ----
class Box<T>(var content: T) {
    fun get(): T = content
    fun put(x: T) {
        content = x
    }
    fun <R> map(f: (T) -> R): Box<R> = Box(f(content))
}

class NumberBox<N : Number>(val n: N) {
    fun asDouble(): Double = n.toDouble()
}

// recursive bound: the constructor cannot be called with erased types, so it is not registered
class SortedBox<C : Comparable<C>>(val c: C) {
    fun describe(): String = "sorted $c"
}

class ListHolder(val items: List<String>) {
    fun countWith(prefix: String, extra: List<String>): Int = (items + extra).count { it.startsWith(prefix) }
    fun transform(f: (String) -> String): List<String> = items.map(f)
}

// ---- enums ----
enum class Colour { RED, GREEN, BLUE }

enum class Planet(val mass: Double) {
    MERCURY(3.3e23), EARTH(5.97e24);

    fun heavierThan(other: Planet): Boolean = mass > other.mass
}

// ---- interfaces ----
interface Shape {
    val area: Double
    fun describe(): String = "shape with area $area"
}

class Square(val side: Double) : Shape {
    override val area: Double get() = side * side
}

// ---- objects ----
object Settings {
    val name = "settings"
    var level = 1
    fun shout(s: String): String = s.uppercase()
}

class WithCompanion(val name: String) {
    companion object {
        val defaultName = "dflt"
        fun create(): WithCompanion = WithCompanion(defaultName)
    }
}

// ---- property order and visibility ----
class Ordered(val zebra: Int, val apple: Int) {
    val mango = 3
    var banana = 4
}

open class OrderedBase {
    val base1 = 1
    val base0 = 0
}

class OrderedSub : OrderedBase() {
    val sub = 2
}

class PrivateSetter {
    var x: Int = 1
        private set

    fun inc() {
        x++
    }
}

// a setter internal to this module cannot be called by the consuming module
class InternalSetter {
    var y: Int = 0
        internal set
}

// a Kotlin function named like a getter is not a property
class WithGetter {
    val a = 1
    fun getTotal(): Int = 5
}

// ---- overloads, including inherited ones (a reference 'OverloadSub::foo' would be ambiguous) ----
open class OverloadBase {
    fun foo(): String = "base foo()"
}

class OverloadSub : OverloadBase() {
    fun foo(x: Int): String = "sub foo($x)"
}

// ---- inner classes of a generic class (written 'Outer<...>.Inner' in generated code) ----
class Outer<T>(val t: T) {
    inner class Inner(var label: String) {
        fun outerValue(): Any? = t
    }

    fun inner(label: String): Inner = Inner(label)
}

class InnerHolder(var inner: Outer<String>.Inner?)

// ---- opt-in: the generated code must opt in ----
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
annotation class ExperimentalThing

class OptInMembers {
    @ExperimentalThing
    fun experimental(): String = "exp"

    @ExperimentalThing
    var experimentalProp: Int = 1

    fun plain(): String = "plain"
}

@ExperimentalThing
class OptInClass(val v: Int)

// ---- deprecated with level ERROR: cannot be referenced, so it is not registered ----
@Deprecated("gone", level = DeprecationLevel.ERROR)
class DeprecatedErrorClass

// ---- overloads with the same number of parameters: chosen by the types of the arguments ----
class SameArity(val tag: String) {
    constructor(n: Int) : this("int:$n")

    fun describe(s: String): String = "String $s"
    fun describe(n: Int): String = "Int $n"
    fun describe(items: List<String>, sep: String = ","): String = items.joinToString(sep)
    fun describe(n: Int, sep: String): String = "Int $n$sep"

    fun <T : Number> num(x: T): String = "Number $x"
    fun num(s: String): String = "String $s"

    // called with one argument, Kotlin uses the overload that needs no default value
    fun width(a: Int): String = "one $a"
    fun width(a: Int, b: Int = 0): String = "two $a $b"
}
