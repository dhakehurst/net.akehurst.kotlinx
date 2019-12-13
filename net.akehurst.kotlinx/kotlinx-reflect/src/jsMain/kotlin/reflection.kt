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

import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

actual fun KFunction<*>.isSuspend() : Boolean = TODO() //this.isSuspend

actual fun <T : Any> proxyFor(forInterface: KClass<*>, invokeMethod: (handler:Any, proxy: Any?, callable: KCallable<*>, methodName:String, args: Array<out Any>) -> Any?): T {
    TODO()
}

actual fun Any.reflect() = ObjectReflection(this)
actual fun KClass<*>.reflect() = ClassReflection(this)

actual object ModuleRegistry {

    val modules = mutableSetOf<Any>()

    // must be inline so that the generated code can find the module as the imported JS name
    actual fun register(moduleName: String) {
        /*
        val module = js("""
            (function() {
              if (typeof __webpack_require__ === 'function') {
                return __webpack_require__('./node_modules/'+moduleName+'/'+moduleName+'.js')
              } else {
                return window[moduleName]
              }
            })()   
        """)
         */
        /* TODO: not sure which order is best!
        First try the 'global/window' namespace
        Then try 'dynamicModule' which should be generated
         */
        val module = js("""
            (function() {
                try {
                    if (window[moduleName]) {
                        return window[moduleName]
                    }
                    var generatedRequire = require('./generatedRequire.js')
                    if (typeof generatedRequire === 'function') {
                        return generatedRequire(moduleName)
                    }
                } catch (e) {
                }
            })()
        """)
        /*
        val module = js("""
            (function() {
                return await import(moduleName)
            })()   
        """)
        */
        modules.add(module)
    }

    fun getJsOb(name: String, obj: Any): Any? {
        return js("obj[name]")
    }

    fun getJsOb(names: List<String>, obj: Any): Any? {
        return if (names.isEmpty()) {
            obj
        } else {
            val child = getJsOb(names[0], obj)
            if (null == child) {
                null
            } else {
                getJsOb(names.drop(1), child)
            }
        }
    }

    actual fun classForName(qualifiedName: String): KClass<*> {
        val path = qualifiedName.split(".")
        modules.forEach {
            val cls = getJsOb(path, it)
            if (null != cls) {
                return (cls as JsClass<*>).kotlin
            }
        }

        throw RuntimeException("Cannot find class $qualifiedName, is the module registered?")
    }

}

actual class ClassReflection<T : Any> actual constructor(val kclass: KClass<T>) {

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

    actual fun construct(vararg constructorArgs: Any?): T {
        val cls = this.kclass.js
//val obj = js("Reflect.construct(cls, ...constructorArgs)")  // ES6
        val obj = js("new (Function.prototype.bind.apply(cls, [null].concat(constructorArgs)))")
        return obj as T
    }

    actual fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean {
        TODO()
    }

    actual fun allPropertyNamesFor(self: T): List<String> {
        val js: Array<String> = js("Object.getOwnPropertyNames(self)")
        return js.toList()
    }

    actual fun allMemberFunctionsFor(self: T): List<KFunction<*>> {
        TODO()
    }

    actual fun isPropertyMutable(propertyName: String): Boolean {
// FIXME: when JS reflection is sufficient
        return true
    }

    actual fun getProperty(self: T, propertyName: String): Any? {
        return js("self[propertyName]")
//return "Reflect.get(obj, propertyName)"
    }

    actual fun setProperty(self: T, propertyName: String, value: Any?) {
        js("self[propertyName] = value")
    }

    actual fun call(self: T, methodName: String, vararg args: Any?): Any? {
        return js("self[methodName](args)")
    }
}

actual class ObjectReflection<T : Any> actual constructor(val self: T) {

    actual val kclass: KClass<T> = self::class as KClass<T>
    actual val isAbstract: Boolean
        get() {
            return this.kclass.simpleName!!.endsWith("Abstract") //TODO !! need something better than this
        }

    actual val allPropertyNames: List<String> by lazy {
        val self = this.self
        val js: Array<String> = js("Object.getOwnPropertyNames(self)")
        js.toList()
    }

    actual fun construct(vararg constructorArgs: Any?): T {
        val cls = this.kclass.js
//val obj = js("Reflect.construct(cls, ...constructorArgs)")  // ES6
        val obj = js("new (Function.prototype.bind.apply(cls, [null].concat(constructorArgs)))")
        return obj as T
    }

    actual fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean {
        TODO()
    }

    actual fun isPropertyMutable(propertyName: String): Boolean {
// FIXME: when JS reflection is sufficient
        return true
    }

    actual fun getProperty(propertyName: String): Any? {
        val self = this.self
        return js("self[propertyName]")
//return "Reflect.get(obj, propertyName)"
    }

    actual fun setProperty(propertyName: String, value: Any?) {
        val self = this.self
        js("self[propertyName] = value")
    }

    actual fun call(methodName: String, vararg args: Any?): Any? {
        val self = this.self
        return js("self[methodName](args)")
    }
}
