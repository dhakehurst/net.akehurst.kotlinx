package net.akehurst.kotlinx.reflect

import kotlin.test.Test
import kotlin.test.assertEquals


open class A {
    val prop1 = "hello"

    override fun hashCode(): Int {
        return prop1.hashCode()
    }
    override fun equals(other: Any?): Boolean {
        return when(other) {
            is A ->this.prop1 == other.prop1
            else -> false
        }
    }
}
open class B : A()
open class C : B()
open class D : B()

class test_reflect {



    @Test
    fun A_isSupertypeOf_B() {
        val actual = A::class.reflect().isSupertypeOf(B::class)
        assertEquals(true, actual)
    }

    @Test
    fun A_isSupertypeOf_C() {
        val actual = A::class.reflect().isSupertypeOf(C::class)
        assertEquals(true, actual)
    }

    @Test
    fun allPropertyNames() {

        val obj1 = A()
        val actual = obj1::class.reflect().allPropertyNames(obj1)
        assertEquals(listOf("prop1"), actual)

    }

    @Test
    fun getProperty() {

        val obj1 = A()
        val actual = obj1::class.reflect().getProperty("prop1", obj1)
        assertEquals(obj1.prop1, actual)

    }

    @Test
    fun construct() {

        val obj1 = A()
        val actual = obj1::class.reflect().construct()
        assertEquals(obj1, actual)

    }

    @Test
    fun classForName() {

        val actual = ModuleRegistry.classForName("net.akehurst.kotlinx.reflect.A")
        assertEquals(A::class, actual)

    }
}
