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

import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1

actual object ModuleRegistry {

    val modules = mutableSetOf<Any>()

    actual fun register(moduleName:String) {
        val module = js("window[moduleName]")
        modules.add(module)
    }

    fun getJsOb(name:String, obj:Any) : Any? {
        return js("obj[name]")
    }

    fun getJsOb(names:List<String>, obj:Any) : Any? {
        return if (names.isEmpty()) {
            obj
        } else {
            val child = getJsOb(names[0], obj)
            if (null==child) {
                null
            } else {
                getJsOb(names.drop(1),child)
            }
        }
    }

    actual fun classForName(qualifiedName:String) :KClass<*> {
        val path = qualifiedName.split(".")
        modules.forEach {
            val cls = getJsOb(path, it)
            if (null!=cls) {
                return (cls as JsClass<*>).kotlin
            }
        }

        throw RuntimeException("Cannot find class $qualifiedName, is the module registered?")
    }

}

actual class Reflection<T : Any> actual constructor(val clazz:KClass<T>) {


    actual val isAbstract:Boolean get() {
        return this.clazz.simpleName!!.endsWith("Abstract")
    }

    actual val allPropertyNames:List<String> by lazy {
        TODO()
    }

    actual fun construct(vararg constructorArgs:Any?) : T {
        val cls = this.clazz.js
        //val obj = js("Reflect.construct(cls, ...constructorArgs)")  // ES6
        val obj = js("new (Function.prototype.bind.apply(cls, [null].concat(constructorArgs)))")
        return obj as T
    }

    actual fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean {
        TODO()
    }

    actual fun allPropertyNames(obj:Any):List<String> {
        val js:Array<String> = js("Object.keys(obj)")
        return js.toList()
    }

    actual fun getProperty(propertyName:String, obj:Any) : Any? {
        return js("obj[propertyName]")
        //return "Reflect.get(obj, propertyName)"
    }
    actual fun setProperty(propertyName:String, obj:Any, value:Any?)  {
        js("obj[propertyName] = value")
    }

}

