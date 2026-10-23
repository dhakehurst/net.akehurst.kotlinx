package net.akehurst.kotlinx.reflect.gradle.plugin.test.own

// Classes declared in this module (not a dependency): 'internal' declarations can be registered.

class OwnThing {
    var a: Int = 1
        internal set
    internal val b: Int = 2
    val c: Int = 3
}

internal class OwnInternal(val v: Int)
