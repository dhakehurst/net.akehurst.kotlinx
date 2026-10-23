package net.akehurst.kotlinx.reflect

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.reflect.KCallable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class test_RegistryJvm {

    @Test
    fun registerModule_secondThreadWaitsUntilRegistrationIsComplete() {
        val started = CountDownLatch(1)
        var completed = false
        var runs = 0
        val first = Thread {
            KotlinxReflect.registerModule("test.slow") {
                runs++
                started.countDown()
                Thread.sleep(200)
                completed = true
            }
        }
        first.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        // must block until the first registration is complete, and not run it again
        KotlinxReflect.registerModule("test.slow") { runs++ }
        assertTrue(completed)
        assertEquals(1, runs)
        first.join()
    }

    @Test
    fun jvmFunctionsKeepTheirClassName_forAlreadyCompiledCallers() {
        // up to 2.3.x these were in net.akehurst.kotlinx.reflect.ReflectionKt
        val facade = Class.forName("net.akehurst.kotlinx.reflect.ReflectionKt")
        assertNotNull(facade.getMethod("methodLiteral", KCallable::class.java))
        assertNotNull(facade.getMethod("defaultValue"))
        assertNotNull(facade.getMethod("reflect", Any::class.java))
    }
}
