import net.akehurst.kotlinx.reflect.gradle.plugin.test.UseReflection
import kotlin.test.Test
import kotlin.test.assertEquals

class test_UseReflection {

    @Test
    fun reflect_construct_simpleName() {

        val sut = UseReflection()

        val actual = sut.reflect_construct_simpleName()

        assertEquals("A", actual)
    }

}