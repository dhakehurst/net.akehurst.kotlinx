package net.akehurst.kotlinx.reflect.gradle.plugin.test.own.testfixtures

import net.akehurst.kotlinx.reflect.KotlinxReflect
import net.akehurst.kotlinx.reflect.reflect
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx_reflect_gradle_plugin_test_commonMain.KotlinxReflectForModule as MainRegistry
import kotlinx_reflect_gradle_plugin_test_commonTest.KotlinxReflectForModule as TestRegistry

// declared in commonTest and matched by a forReflectionMain glob ('own.**'): only the test registry can see it
class OwnFixture(val f: Int)

/** The test registry must give the same answers as the commonMain registry for main classes, in whatever order they are called. */
class test_TestRegistry {

    private val own = "net.akehurst.kotlinx.reflect.gradle.plugin.test.own"

    @BeforeTest
    fun setup() {
        KotlinxReflect.forceRegistry = true
        TestRegistry.registerUsedClasses()
    }

    @Test
    fun mainSubPackage_reachedByDoubleStar() {
        assertEquals(4, KotlinxReflect.classForName("$own.sub.Deep").reflect().construct(4).reflect().getProperty("d"))
    }

    @Test
    fun mainInternalClass() {
        assertEquals(5, KotlinxReflect.classForName("$own.OwnInternal").reflect().construct(5).reflect().getProperty("v"))
    }

    @Test
    fun mainInternalMembers_notOverwritten() {
        MainRegistry.registerUsedClasses()
        TestRegistry.registerUsedClasses()
        val cr = KotlinxReflect.classForName("$own.OwnThing").reflect()
        assertEquals(listOf("a", "b", "c"), cr.allPropertyNames)
        assertTrue(cr.isPropertyMutable("a"))
    }

    @Test
    fun testClassMatchingMainGlob() {
        assertEquals(6, KotlinxReflect.classForName("$own.testfixtures.OwnFixture").reflect().construct(6).reflect().getProperty("f"))
    }
}
