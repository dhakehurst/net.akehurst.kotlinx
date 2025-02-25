/**
 * Copyright (C) 2024 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

actual fun KFunction<*>.isSuspend(): Boolean = TODO() //this.isSuspend

actual fun <T : Any> proxyFor(forInterface: KClass<*>, invokeMethod: (handler: Any, proxy: Any?, callable: KCallable<*>, methodName: String, args: Array<out Any>) -> Any?): T {
    TODO()
}

actual fun Any.reflect() = ObjectReflection(this)
actual fun KClass<*>.reflect() = ClassReflection(this)


actual class ClassReflection<T : Any> actual constructor(val kclass: KClass<T>) {

    companion object {
    }

    actual val isAbstract: Boolean
        get() {
            return this.kclass.simpleName!!.endsWith("Abstract")
        }

    actual val allPropertyNames: List<String> by lazy {
        TODO()
    }

    actual val allMemberFunctions: List<KFunction<*>> by lazy {
        TODO()
    }

    actual val qualifiedName: String get() = KotlinxReflect.qualifiedNameForClass(this.kclass)

    actual val isEnum: Boolean
        get() {
            TODO()
        }

    actual val isObject: Boolean
        get() {
            TODO()
        }

    actual fun construct(vararg constructorArgs: Any?): T {
        TODO()
    }

    actual fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean {
        TODO()
    }

    actual fun allPropertyNamesFor(self: T): List<String> {
        TODO()
    }

    actual fun isPropertyMutable(propertyName: String): Boolean {
        TODO()
    }

    actual fun getProperty(self: T, propertyName: String): Any? {
        TODO()
    }

    actual fun setProperty(self: T, propertyName: String, value: Any?) {
        TODO()
    }

    actual fun <E : Enum<E>> enumValues(): List<E> {
        return if (isEnum) {
            //val jsCls = this.kclass.js.asDynamic()
            //jsCls.values().unsafeCast<Array<E>>().asList()
            KotlinxReflect.enumValues(this.qualifiedName) as List<E>
        } else {
            emptyList()
        }
    }

    actual fun <E : Enum<E>> enumValueOf(name: String): E? {
        return this.enumValues().firstOrNull { it.name == name } as E?
    }

    actual fun call(self: T, methodName: String, vararg args: Any?): Any? {
        TODO()// return js("self[methodName](args)")
    }
}

actual class ObjectReflection<T : Any> actual constructor(val self: T) {

    actual val kclass: KClass<T> = self::class as KClass<T>
    actual val isAbstract: Boolean
        get() {
            return this.kclass.simpleName!!.endsWith("Abstract") //TODO !! need something better than this
        }

    actual val isProxy: Boolean get() = TODO()

    actual val allPropertyNames: List<String> by lazy {
        TODO()
    }

    actual fun construct(vararg constructorArgs: Any?): T {
       TODO()
    }

    actual fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean {
        TODO()
    }

    actual fun isPropertyMutable(propertyName: String): Boolean {
        TODO()
    }

    actual fun getProperty(propertyName: String): Any? {
        TODO()
    }

    actual fun setProperty(propertyName: String, value: Any?) {
        TODO()
    }

    actual fun call(methodName: String, vararg args: Any?): Any? {
        TODO()
    }

    actual suspend fun callSuspend(methodName: String, vararg args: Any?): Any? {
        TODO()
    }
}
