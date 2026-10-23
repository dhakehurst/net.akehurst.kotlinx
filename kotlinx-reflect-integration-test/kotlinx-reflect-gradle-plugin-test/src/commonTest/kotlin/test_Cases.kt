import net.akehurst.kotlinx.reflect.KotlinxReflect
import net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.*
import net.akehurst.kotlinx.reflect.reflect
import kotlin.test.*

// Classes from a dependency with no plugins, registered only by the generated code.

class test_Suspend {
    @BeforeTest
    fun setup() = useGeneratedRegistrations()

    @Test
    fun callSuspend() {
        assertEquals(15, runSuspend { SuspendThing(10).reflect().callSuspend("add", 5) })
        assertEquals("hello", runSuspend { SuspendThing(10).reflect().callSuspend("hello") })
    }

    @Test
    fun call_onSuspend_fails() {
        val e = assertFailsWith<IllegalStateException> { SuspendThing(10).reflect().call("add", 5) }
        assertEquals("Method 'add' on class 'SuspendThing' is a suspend function, use 'callSuspend' instead of 'call'", e.message)
    }

    @Test
    fun callSuspend_onNonSuspend_fails() {
        val e = assertFailsWith<IllegalStateException> { runSuspend { SuspendThing(10).reflect().callSuspend("notSuspend") } }
        assertEquals("Method 'notSuspend' on class 'SuspendThing' is not a suspend function, use 'call' instead of 'callSuspend'", e.message)
    }
}

class test_ValueClasses {
    @BeforeTest
    fun setup() = useGeneratedRegistrations()

    @Test
    fun valueClass_itself() {
        val id = KotlinxReflect.classForName("$PKG.Id").reflect().construct("abc")
        assertEquals(Id("abc"), id)
        assertEquals("abc", id.reflect().getProperty("value"))
    }

    @Test
    fun property_ofValueClassType() {
        val h = Holder(Id("a"))
        assertEquals(Id("a"), h.reflect().getProperty("id"))
        assertEquals(null, h.reflect().getProperty("other"))
        h.reflect().setProperty("other", Id("b"))
        assertEquals(Id("b"), h.other)
    }

    @Test
    fun function_withValueClassParameter() {
        val h2 = Holder(Id("a")).reflect().call("rename", Id("z")) as Holder
        assertEquals(Id("z"), h2.id)
        assertEquals("z", h2.reflect().call("idValue"))
    }

    @Test
    fun constructor_withValueClassParameters() {
        val h = Holder::class.reflect().construct(Id("x"), Id("y")) as Holder
        assertEquals(Id("y"), h.other)
    }
}

class test_Generics {
    @BeforeTest
    fun setup() = useGeneratedRegistrations()

    @Test
    fun genericClass() {
        val box = Box::class.reflect().construct("hello") as Box<*>
        assertEquals("hello", box.reflect().getProperty("content"))
        box.reflect().setProperty("content", 42)
        assertEquals(42, box.reflect().call("get"))
        box.reflect().call("put", "again")
        assertEquals("again", box.content)
    }

    @Test
    fun genericFunction() {
        val box = Box("abc")
        val mapped = box.reflect().call("map", { s: Any? -> (s as String).length }) as Box<*>
        assertEquals(3, mapped.content)
    }

    @Test
    fun boundedTypeParameter() {
        val nb = NumberBox::class.reflect().construct(2.5) as NumberBox<*>
        assertEquals(2.5, nb.reflect().call("asDouble"))
    }

    @Test
    fun recursiveBound_constructorNotRegistered_butMembersAre() {
        val e = assertFailsWith<IllegalStateException> { SortedBox::class.reflect().construct("a") }
        assertEquals("No constructor with 1 parameter(s) found for class 'SortedBox'", e.message)
        assertEquals("a", SortedBox("a").reflect().getProperty("c"))
        assertEquals("sorted a", SortedBox("a").reflect().call("describe"))
    }

    @Test
    fun parameterisedParameterTypes() {
        val lh = ListHolder(listOf("apple", "avocado", "banana"))
        assertEquals(3, lh.reflect().call("countWith", "a", listOf("apricot")))
        assertEquals(listOf("APPLE", "AVOCADO", "BANANA"), lh.reflect().call("transform", { s: String -> s.uppercase() }))
    }
}

class test_Enums {
    @BeforeTest
    fun setup() = useGeneratedRegistrations()

    @Test
    fun simpleEnum() {
        val cr = KotlinxReflect.classForName("$PKG.Colour").reflect()
        assertTrue(cr.isEnum)
        assertEquals(Colour.entries, cr.enumValues<Colour>())
        assertEquals(Colour.GREEN, cr.enumValueOf<Colour>("GREEN"))
    }

    @Test
    fun previouslyBrokenEnum() {
        assertEquals(EEEE.entries, EEEE::class.reflect().enumValues<EEEE>())
    }

    @Test
    fun enumWithPropertiesAndFunctions() {
        assertEquals(5.97e24, Planet.EARTH.reflect().getProperty("mass"))
        assertEquals(true, Planet.EARTH.reflect().call("heavierThan", Planet.MERCURY))
        assertEquals(listOf("mass"), Planet::class.reflect().allPropertyNames)
    }

    @Test
    fun enumIsNotConstructable() {
        assertFailsWith<IllegalStateException> { Colour::class.reflect().construct() }
    }
}

class test_Interfaces {
    @BeforeTest
    fun setup() = useGeneratedRegistrations()

    @Test
    fun interfaceFlags() {
        val cr = Shape::class.reflect()
        assertTrue(cr.isInterface)
        assertTrue(cr.isAbstract)
        assertFalse(Square::class.reflect().isInterface)
    }

    @Test
    fun isSupertypeOf() {
        assertTrue(Shape::class.reflect().isSupertypeOf(Square::class))
        assertFalse(Square::class.reflect().isSupertypeOf(Shape::class))
    }

    @Test
    fun propertyOverriddenFromInterface() {
        assertEquals(4.0, Square(2.0).reflect().getProperty("area"))
    }

    @Test
    fun defaultMethodInheritedFromInterface() {
        // 2.25 prints the same on every platform (JS prints 9.0 as "9")
        assertEquals("shape with area 2.25", Square(1.5).reflect().call("describe"))
    }
}

class test_Objects {
    @BeforeTest
    fun setup() = useGeneratedRegistrations()

    @Test
    fun objectDeclaration() {
        val cr = KotlinxReflect.classForName("$PKG.Settings").reflect()
        assertTrue(cr.isObject)
        assertSame(Settings, cr.construct())
    }

    @Test
    fun objectMembers() {
        assertEquals("settings", Settings.reflect().getProperty("name"))
        Settings.reflect().setProperty("level", 5)
        assertEquals(5, Settings.level)
        assertEquals("HI", Settings.reflect().call("shout", "hi"))
    }

    @Test
    fun companionObject() {
        val companion = KotlinxReflect.classForName("$PKG.WithCompanion.Companion").reflect().construct()
        assertSame(WithCompanion, companion)
        assertEquals("dflt", companion.reflect().getProperty("defaultName"))
        assertEquals("dflt", (companion.reflect().call("create") as WithCompanion).name)
    }
}

class test_PropertyOrder {
    @BeforeTest
    fun setup() = useGeneratedRegistrations()

    @Test
    fun declarationOrder() {
        assertEquals(listOf("zebra", "apple", "mango", "banana"), Ordered(1, 2).reflect().allPropertyNames)
    }

    @Test
    fun inheritedFirst() {
        assertEquals(listOf("base1", "base0", "sub"), OrderedSub().reflect().allPropertyNames)
    }

    @Test
    fun internalSetterInLibrary_isNotMutable() {
        assertFalse(InternalSetter().reflect().isPropertyMutable("y"))
        assertEquals(0, InternalSetter().reflect().getProperty("y"))
    }

    @Test
    fun kotlinGetFunction_isNotAProperty() {
        assertEquals(listOf("a"), WithGetter().reflect().allPropertyNames)
        assertEquals(5, WithGetter().reflect().call("getTotal"))
    }

    @Test
    fun privateSetter_isNotMutable() {
        val obj = PrivateSetter()
        assertFalse(obj.reflect().isPropertyMutable("x"))
        val e = assertFailsWith<IllegalStateException> { obj.reflect().setProperty("x", 5) }
        assertEquals("Property 'x' on class 'PrivateSetter' is not mutable", e.message)
    }
}
