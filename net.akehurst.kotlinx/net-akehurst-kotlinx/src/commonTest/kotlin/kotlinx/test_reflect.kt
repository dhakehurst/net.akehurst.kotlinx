package net.akehurst.kotlinx.reflect

import kotlin.test.Test
import kotlin.test.assertEquals


class test_reflect {

    open class A
    open class B : A()
    open class C : B()
    open class D : B()

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
}
