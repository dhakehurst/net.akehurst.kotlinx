import kotlinx_reflect_gradle_plugin_test_moduleForReflection_commonMain.KotlinxReflectForModule
import net.akehurst.kotlinx.reflect.gradle.plugin.test.UseReflection
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class test_UseReflection {

    @BeforeTest
    fun setup() {
        KotlinxReflectForModule.registerUsedClasses()
    }

    @Test
    fun reflect_construct_simpleName() {

        val sut = UseReflection()

        val actual = sut.reflect_construct_simpleName()

        assertEquals("AAAA", actual)
    }

    @Test
    fun reflect_access_property() {

        val sut = UseReflection()

        val actual = sut.reflect_access_property()

        assertEquals("hello", actual)
    }
}