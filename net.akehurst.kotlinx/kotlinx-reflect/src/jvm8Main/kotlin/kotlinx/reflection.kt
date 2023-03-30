/**
 * Copyright (C) 2019 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.akehurst.kotlinx.reflect

import net.akehurst.kotlinx.collections.transitveClosure
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.reflect.*
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaMethod
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.reflect.full.*
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.kotlinFunction

fun <T : Any> defaultValue(): T {
    return castNull()
}

fun <T : Any> createInstance(kClass: KClass<T>): T {
    return castNull()
}

/**
 * from [https://github.com/nhaarman/mockito-kotlin/blob/2.x/mockito-kotlin/src/main/kotlin/com/nhaarman/mockitokotlin2/internal/CreateInstance.kt]
 * Uses a quirk in the bytecode generated by Kotlin
 * to cast [null] to a non-null type.
 *
 * See https://youtrack.jetbrains.com/issue/KT-8135.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> castNull(): T = null as T

fun <R> methodLiteral(call: KCallable<R>): Method? {
    return when (call) {
        is KFunction<R> -> call.javaMethod
        else -> null
    }
}

actual fun KFunction<*>.isSuspend(): Boolean = this.isSuspend

actual fun <T : Any> proxyFor(forInterface: KClass<*>, invokeMethod: (handler: Any, proxy: Any?, callable: KCallable<*>, methodName: String, args: Array<out Any>) -> Any?): T {
    val handler = object : InvocationHandler {
        override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
            val args2 = args ?: emptyArray<Any>()
            //this throws an error if one of the parameters is an inline class
            // see [https://youtrack.jetbrains.com/issue/KT-34024]
            val callable = method?.kotlinFunction!!
            /*
                        //FIXME using workaround by just checking for method name, might fail if duplicate names
                        val callable = method?.declaringClass?.kotlin?.members?.firstOrNull {
                            //need to handle jvm name mangling!!

                            if (it.name.contains('-') or method.name.contains('-')) {
                                it.name.substringBefore('-') == method.name.substringBefore('-')
                            } else {
                                it.name == method.name
                            }

                        } ?: throw RuntimeException("method not found $method")

             */
            return invokeMethod.invoke(this, proxy, callable, method.name, args2)
        }
    }
    val proxy = Proxy.newProxyInstance(forInterface.java.classLoader, arrayOf(forInterface.java), handler)
    return proxy as T
}

actual fun Any.reflect() = ObjectReflection(this)
actual fun KClass<*>.reflect() = ClassReflection(this)

actual class ClassReflection<T : Any> actual constructor(val kclass: KClass<T>) {

    actual val isAbstract: Boolean = this.kclass.isAbstract

    actual val allPropertyNames: List<String> by lazy {
        //find get methods, for java defined classes/properties
        val methProps = this.kclass.memberFunctions.filter { it.name.startsWith("get") && it.valueParameters.size == 0 }.map { it.name.substring(3).decapitalize() }
        val publicProps = this.kclass.memberProperties.filter { it.visibility == KVisibility.PUBLIC }.map { it.name }
        (publicProps + methProps).toSet().toList()
    }

  //  actual val allMemberFunctions: List<KFunction<*>> by lazy {
  //      this.kclass.memberFunctions.filter { it.visibility == KVisibility.PUBLIC }
 //   }

    actual val qualifiedName: String = this.kclass.qualifiedName ?: error("Cannot get qualifiedName of '${this.kclass}'")

    actual fun construct(vararg constructorArgs: Any?): T {
        return this.kclass.constructors.first {
            //TODO: check types match
            it.parameters.size == constructorArgs.size
        }.call(*constructorArgs)
    }

    actual fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean {
        return this.kclass == subtype
                || subtype.supertypes.toSet().transitveClosure {
            val cls = it.classifier
            if (cls is KClass<*>) {
                cls.supertypes.toSet()
            } else {
                emptySet<KType>()
            }
        }.any { it.classifier == this.kclass }
    }

    actual fun allPropertyNamesFor(self: T): List<String> {
        if (this.kclass.isInstance(self)) {
            return this.allPropertyNames
        } else {
            throw RuntimeException("$self is not an instance of ${this.kclass}")
        }
    }

 //   actual fun allMemberFunctionsFor(self: T): List<KFunction<*>> {
 //       if (this.kclass.isInstance(self)) {
 //           return this.allMemberFunctions
 //       } else {
 //           throw RuntimeException("$self is not an instance of ${this.kclass}")
 //       }
 //   }

    actual fun isPropertyMutable(propertyName: String): Boolean {
        val mprop = this.kclass.memberProperties.firstOrNull { propertyName == it.name }
        if (null != mprop) {
            return mprop is KMutableProperty1<*, *>
        } else {
            val mmeth = this.kclass.memberFunctions.firstOrNull { it.name == "set${propertyName.capitalize()}" && it.valueParameters.size == 1 }
            return mmeth != null
        }
    }

    actual fun getProperty(self: T, propertyName: String): Any? {
        val mprop = self::class.memberProperties.firstOrNull { propertyName == it.name }
        if (null != mprop) {
            val prop = mprop as KProperty1<Any, *>
            prop.getter.isAccessible = true
            return prop.getter.call(self)
        } else {
            val mmeth = self::class.memberFunctions.firstOrNull { it.name == "get${propertyName.capitalize()}" && it.valueParameters.size == 0 }
            if (mmeth == null) {
                throw RuntimeException("Property ${propertyName} not found on object ${self}")
            } else {
                mmeth.isAccessible = true
                return mmeth.call(self)
            }
        }
    }

    actual fun setProperty(self: T, propertyName: String, value: Any?) {
        val mprop = self::class.memberProperties.firstOrNull { propertyName == it.name }
        if (mprop != null) {
            val prop = mprop as KMutableProperty1<Any, Any?>
            prop.setter.isAccessible = true
            prop.setter.call(self, value)
        } else {
            val mmeth = self::class.memberFunctions.firstOrNull { it.name == "set${propertyName.capitalize()}" && it.valueParameters.size == 1 }
            //TODO: check type of parameter!
            if (mmeth == null) {
                throw RuntimeException("Property ${propertyName} not settable on object ${self}")
            } else {
                mmeth.isAccessible = true
                mmeth.call(self, value)
            }
        }
    }

    actual val isEnum: Boolean get() = this.kclass.java.isEnum

    actual fun <E : Enum<E>> enumValues(): List<E> =
        (this.kclass.java.enumConstants as Array<E>).asList()

    actual fun <E : Enum<E>> enumValueOf(name: String): E? {
        return this.enumValues().firstOrNull { it.name == name } as E?
    }

    actual fun call(self: T, methodName: String, vararg args: Any?): Any? {
        return self.reflect().call(methodName, *args)
    }
}

actual class ObjectReflection<T : Any> actual constructor(val self: T) {

    actual val kclass: KClass<T> = self::class as KClass<T>
    actual val isAbstract: Boolean = this.kclass.isAbstract

    actual val allPropertyNames: List<String> by lazy {
        //find get methods, for java defined classes/properties
        val methProps = this.kclass.memberFunctions.filter { it.name.startsWith("get") && it.valueParameters.size == 0 }.map { it.name.substring(3).decapitalize() }
        val publicProps = this.kclass.memberProperties.filter { it.visibility == KVisibility.PUBLIC }.map { it.name }
        (publicProps + methProps).toSet().toList()
    }

    actual fun construct(vararg constructorArgs: Any?): T {
        return this.kclass.constructors.first {
            //TODO: check types match
            it.parameters.size == constructorArgs.size
        }.call(*constructorArgs)
    }

    actual fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean {
        return this.kclass == subtype
                || subtype.supertypes.toSet().transitveClosure {
            val cls = it.classifier
            if (cls is KClass<*>) {
                cls.supertypes.toSet()
            } else {
                emptySet<KType>()
            }
        }.any { it.classifier == this.kclass }
    }

    actual fun isPropertyMutable(propertyName: String): Boolean {
        val mprop = this.kclass.memberProperties.firstOrNull { propertyName == it.name }
        if (null != mprop) {
            return mprop is KMutableProperty1<*, *>
        } else {
            val mmeth = this.kclass.memberFunctions.firstOrNull { it.name == "set${propertyName.capitalize()}" && it.valueParameters.size == 1 }
            return mmeth != null
        }
    }

    actual fun getProperty(propertyName: String): Any? {
        val mprop = kclass.memberProperties.firstOrNull { propertyName == it.name }
        if (null != mprop) {
            //first do null check because of bug with properties with type inline class
            if (null == mprop.javaGetter?.invoke(self)) {
                return null;
            }
            val prop = mprop as KProperty1<Any, *>
            prop.getter.isAccessible = true
            return prop.getter.call(self)
        } else {
            val mmeth = kclass.memberFunctions.firstOrNull { it.name == "get${propertyName.capitalize()}" && it.valueParameters.size == 0 }
            if (mmeth == null) {
                throw RuntimeException("Property ${propertyName} not found on object ${self}")
            } else {
                mmeth.isAccessible = true
                return mmeth.call(self)
            }
        }
    }

    actual fun setProperty(propertyName: String, value: Any?) {
        val mprop = kclass.memberProperties.firstOrNull { propertyName == it.name }
        if (mprop != null) {
            val prop = mprop as KMutableProperty1<Any, Any?>
            prop.setter.isAccessible = true
            prop.setter.call(self, value)
        } else {
            val mmeth = kclass.memberFunctions.firstOrNull { it.name == "set${propertyName.capitalize()}" && it.valueParameters.size == 1 }
            //TODO: check type of parameter!
            if (mmeth == null) {
                throw RuntimeException("Property ${propertyName} not settable on object ${self}")
            } else {
                mmeth.isAccessible = true
                mmeth.call(self, value)
            }
        }
    }

    actual fun call(methodName: String, vararg args: Any?): Any? {
        // because of methods created by kotlin to support suspend functions and inline classes, we must look for the jvm method direct,
        // and check for a mangled name version if normal one fails!

        // First try it the nice way
        try {
            val meth = kclass.memberFunctions.firstOrNull { methodName == it.name }
            if (null != meth) {
                return meth.call(self, *args)
            } else {
                throw RuntimeException("Method ${methodName} not found on object ${self}")
            }
        } catch (t: Throwable) {
            val e = if (t is InvocationTargetException && null == t.cause) t else t.cause
            val msg = "call($methodName) failed: ${e}"
            println(msg)
        }

        //then try the JVM way
        try {
            val meth = kclass.java.methods.firstOrNull { methodName == it.name }
            if (null != meth) {
                return meth.invoke(self, *args)
            } else {
                throw RuntimeException("Method ${methodName} not found on object ${self}")
            }
        } catch (t: Throwable) {
            val e = if (t is InvocationTargetException && null == t.cause) t else t.cause
            val msg = "call($methodName) failed: ${e}"
            println(msg)
        }

        // finally try for a mangled name version
        try {
            val meth = kclass.java.methods.firstOrNull { it.name.startsWith(methodName + "-") }
            val unBoxedArgs = meth!!.parameterTypes.mapIndexed { index, clazz ->
                val arg = args[index]
                when {
                    null == arg -> null
                    clazz.isInstance(arg) -> arg
                    else -> arg::class.memberProperties.first().call(arg) //their should only be one property on an inline class
                }
            }
            if (null != meth) {
                return meth.invoke(self, *unBoxedArgs.toTypedArray())
            } else {
                throw RuntimeException("Method ${methodName} not found on object ${self}")
            }
        } catch (t: Throwable) {
            if (t is InvocationTargetException && null != t.cause) {
                throw t
            } else {
                val msg = "call($methodName) failed: ${t}"
                println(msg)
            }
        }

        throw RuntimeException("Method ${methodName} not found on object ${self}")
    }

    actual suspend fun callSuspend(methodName: String, vararg args: Any?): Any? {
        // because of methods created by kotlin to support suspend functions and inline classes, we must look for the jvm method direct,
        // and check for a mangled name version if normal one fails!

        // First try it the nice way
        try {
            val meth = kclass.memberFunctions.firstOrNull { methodName == it.name }
            if (null != meth) {
                return suspendCoroutineUninterceptedOrReturn { cont ->
                    return@suspendCoroutineUninterceptedOrReturn meth.call(self, *args, cont)
                }
            } else {
                throw RuntimeException("Method ${methodName} not found on object ${self}")
            }
        } catch (t: Throwable) {
            val e = if (t is InvocationTargetException && null == t.cause) t else t.cause
            val msg = "callSuspend($methodName) failed: ${e}"
            println(msg)
        }

        //then try the JVM way
        try {
            val meth = kclass.java.methods.firstOrNull { methodName == it.name }
            if (null != meth) {
                return suspendCoroutineUninterceptedOrReturn { cont ->
                    return@suspendCoroutineUninterceptedOrReturn meth.invoke(self, *args, cont)
                }
            } else {
                throw RuntimeException("Method ${methodName} not found on object ${self}")
            }
        } catch (t: Throwable) {
            val e = if (t is InvocationTargetException && null == t.cause) t else t.cause
            val msg = "callSuspend($methodName) failed: ${e}"
            println(msg)
        }

        // finally try for a mangled name version
        try {
            val meth = kclass.java.methods.firstOrNull { it.name.startsWith(methodName + "-") }
            val unBoxedArgs = meth!!.parameterTypes.dropLast(1) //drop the 'Continuation' parameter, no need to check that
                .mapIndexed { index, clazz ->
                    val arg = args[index]
                    when {
                        null == arg -> null
                        clazz.isInstance(arg) -> arg
                        else -> arg::class.memberProperties.first().call(arg) //their should only be one property on an inline class
                    }
                }
            if (null != meth) {
                return suspendCoroutineUninterceptedOrReturn { cont ->
                    return@suspendCoroutineUninterceptedOrReturn meth.invoke(self, *unBoxedArgs.toTypedArray(), cont)
                }
            } else {
                throw RuntimeException("Method ${methodName} not found on object ${self}")
            }
        } catch (t: Throwable) {
            if (t is InvocationTargetException && null != t.cause) {
                throw t
            } else {
                val msg = "callSuspend($methodName) failed: ${t}"
                println(msg)
            }
        }

        throw RuntimeException("Method ${methodName} not found on object ${self}")
    }

}


