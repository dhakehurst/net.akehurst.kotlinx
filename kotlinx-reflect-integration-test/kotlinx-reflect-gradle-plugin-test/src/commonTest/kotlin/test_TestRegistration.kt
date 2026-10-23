package net.akehurst.kotlinx.reflect.gradle.plugin.test.testonly

import net.akehurst.kotlinx.reflect.KotlinxReflect
import kotlinx_reflect_gradle_plugin_test_commonTest.KotlinxReflectForModule
import net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.CCCC
import net.akehurst.kotlinx.reflect.reflect
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A class that exists only in commonTest. */
class TestOnly(val x: Int) {
    fun double() = x * 2
}

/**
 * Test registrations are generated per target (KSP has no commonTest metadata compilation),
 * as the 'actual' of an 'expect fun' declared in commonTest.
 */
class test_TestRegistration {

    @BeforeTest
    fun setup() {
        KotlinxReflect.forceRegistry = true
        KotlinxReflectForModule.registerUsedClasses()
    }

    @Test
    fun testOnlyClass() {
        val obj = KotlinxReflect.classForName("net.akehurst.kotlinx.reflect.gradle.plugin.test.testonly.TestOnly").reflect().construct(21)
        assertEquals(21, obj.reflect().getProperty("x"))
        assertEquals(42, obj.reflect().call("double"))
    }

    @Test
    fun mainClassesAlsoRegistered() {
        assertEquals("hello bob", CCCC("bob").reflect().call("greet", "hello"))
    }
}
