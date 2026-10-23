import net.akehurst.kotlinx.reflect.KotlinxReflect
import net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.*
import net.akehurst.kotlinx.reflect.reflect
import kotlin.test.*

// Overloads registered by the generated code, chosen by the types of the arguments.

class test_SameArityOverloads {
    @BeforeTest
    fun setup() = useGeneratedRegistrations()

    @Test
    fun constructors() {
        val cr = KotlinxReflect.classForName("$PKG.SameArity").reflect()
        assertEquals("a", (cr.construct("a") as SameArity).tag)
        assertEquals("int:5", (cr.construct(5) as SameArity).tag)
    }

    @Test
    fun functions_oneArgument() {
        val o = SameArity("x").reflect()
        assertEquals("String s", o.call("describe", "s"))
        assertEquals("Int 5", o.call("describe", 5))
        assertEquals("a,b", o.call("describe", listOf("a", "b")))
    }

    @Test
    fun functions_twoArguments() {
        val o = SameArity("x").reflect()
        assertEquals("a;b", o.call("describe", listOf("a", "b"), ";"))
        assertEquals("Int 5;", o.call("describe", 5, ";"))
    }

    @Test
    fun genericParameter_checkedAgainstItsBound() {
        val o = SameArity("x").reflect()
        assertEquals("Number 5", o.call("num", 5))
        assertEquals("String s", o.call("num", "s"))
    }

    @Test
    fun defaultValues_overloadWithoutThemWins() {
        val o = SameArity("x").reflect()
        assertEquals("one 1", o.call("width", 1))
        assertEquals("two 1 2", o.call("width", 1, 2))
    }

    @Test
    fun noMatch() {
        val e = assertFailsWith<IllegalStateException> { SameArity("x").reflect().call("describe", true) }
        assertEquals("No overload of method 'describe' on class 'SameArity' accepts arguments (Boolean)", e.message)
    }
}
