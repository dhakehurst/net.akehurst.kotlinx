import net.akehurst.kotlinx.reflect.KotlinxReflect
import net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.*
import net.akehurst.kotlinx.reflect.reflect
import kotlin.test.*

// Declarations that the generated code once failed to compile for. That this file's module compiles is part of the test.

class test_InheritedOverload {
    @BeforeTest
    fun setup() = useGeneratedRegistrations()

    @Test
    fun call_declaredAndInherited() {
        assertEquals("sub foo(1)", OverloadSub().reflect().call("foo", 1))
        assertEquals("base foo()", OverloadSub().reflect().call("foo"))
    }

    @Test
    fun overloadedFunction_hasNoReference() {
        val fn = KotlinxReflect.registeredClass(OverloadSub::class)!!.functions["foo"]!!.single()
        assertNull(fn.reference)
    }
}

class test_InnerOfGeneric {
    @BeforeTest
    fun setup() = useGeneratedRegistrations()

    @Test
    fun property_getAndSet() {
        val inner = Outer("x").inner("a")
        assertEquals("a", inner.reflect().getProperty("label"))
        inner.reflect().setProperty("label", "b")
        assertEquals("b", inner.label)
    }

    @Test
    fun function() {
        assertEquals("x", Outer("x").inner("a").reflect().call("outerValue"))
    }

    @Test
    fun noConstructor() {
        assertFailsWith<IllegalStateException> { Outer.Inner::class.reflect().construct("a") }
    }

    @Test
    fun propertyOfInnerType() {
        val inner = Outer("x").inner("a")
        val holder = InnerHolder(null)
        holder.reflect().setProperty("inner", inner)
        assertSame(inner, holder.reflect().getProperty("inner"))
    }
}

class test_OptIn {
    @BeforeTest
    fun setup() = useGeneratedRegistrations()

    @Test
    fun members() {
        val o = OptInMembers()
        assertEquals("exp", o.reflect().call("experimental"))
        o.reflect().setProperty("experimentalProp", 5)
        assertEquals(5, o.reflect().getProperty("experimentalProp"))
        assertEquals("plain", o.reflect().call("plain"))
    }

    @Test
    fun cls() {
        val obj = KotlinxReflect.classForName("$PKG.OptInClass").reflect().construct(3)
        assertEquals(3, obj.reflect().getProperty("v"))
    }
}

class test_DeprecatedError {
    @BeforeTest
    fun setup() = useGeneratedRegistrations()

    @Test
    fun notRegistered() {
        assertFailsWith<IllegalStateException> { KotlinxReflect.classForName("$PKG.DeprecatedErrorClass") }
    }
}
