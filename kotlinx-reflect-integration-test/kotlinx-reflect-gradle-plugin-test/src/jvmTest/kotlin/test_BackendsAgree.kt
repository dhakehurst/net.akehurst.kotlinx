import kotlinx_reflect_gradle_plugin_test_commonMain.KotlinxReflectForModule
import net.akehurst.kotlinx.reflect.KotlinxReflect
import net.akehurst.kotlinx.reflect.gradle.plugin.test.moduleForReflection.*
import net.akehurst.kotlinx.reflect.reflect
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * On JVM, kotlin-reflect is the reference: for every class, the answers using only the generated
 * registrations (forceRegistry = true) must be the same as the answers from kotlin-reflect.
 * Any difference means the KSP processor generated something wrong or missed something.
 */
class test_BackendsAgree {

    private val classes: List<KClass<*>> = listOf(
        AAAA::class, BBBB::class, CCCC::class, EEEE::class, SSSS::class, SSSS.red::class,
        SuspendThing::class, Id::class, Holder::class,
        Box::class, NumberBox::class, SortedBox::class, ListHolder::class,
        Colour::class, Planet::class, Shape::class, Square::class,
        Settings::class, WithCompanion::class, WithCompanion.Companion::class,
        Ordered::class, OrderedBase::class, OrderedSub::class, PrivateSetter::class, InternalSetter::class, WithGetter::class,
        OverloadBase::class, OverloadSub::class, Outer::class, Outer.Inner::class, InnerHolder::class, OptInMembers::class,
    )

    @BeforeTest
    fun setup() = KotlinxReflectForModule.registerUsedClasses()

    @AfterTest
    fun after() {
        KotlinxReflect.forceRegistry = false
    }

    /** Everything we can ask about a class without an instance. */
    private fun describe(k: KClass<*>): Map<String, Any?> {
        val cr = k.reflect()
        val props = cr.allPropertyNames
        return linkedMapOf(
            "qualifiedName" to cr.qualifiedName,
            "isAbstract" to cr.isAbstract,
            "isInterface" to cr.isInterface,
            "isEnum" to cr.isEnum,
            "isObject" to cr.isObject,
            "allPropertyNames" to props,
            "mutable" to props.associateWith { cr.isPropertyMutable(it) },
            "enumValues" to (cr.enumValues<Colour>() as List<*>).map { (it as Enum<*>).name },
            "supertypeOf" to classes.filter { cr.isSupertypeOf(it) }.map { it.simpleName },
        )
    }

    private fun withBackend(forceRegistry: Boolean): Map<String, Map<String, Any?>> {
        KotlinxReflect.forceRegistry = forceRegistry
        return classes.associate { it.qualifiedName!! to describe(it) }
    }

    @Test
    fun registryMatchesKotlinReflect() {
        val kotlinReflect = withBackend(false)
        val registry = withBackend(true)
        val differences = kotlinReflect.keys.flatMap { cls ->
            kotlinReflect[cls]!!.keys.mapNotNull { key ->
                val a = kotlinReflect[cls]!![key]
                val b = registry[cls]!![key]
                if (a == b) null else "$cls.$key: kotlin-reflect=$a registry=$b"
            }
        }
        assertEquals("", differences.joinToString("\n"))
    }
}
