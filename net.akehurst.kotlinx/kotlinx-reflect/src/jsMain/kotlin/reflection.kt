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

val IR:Boolean by lazy {
    val pair = Pair(1,2)
    val result:Boolean = js("pair.hasOwnProperty('_first')")
    result
}

actual fun KFunction<*>.isSuspend() : Boolean = TODO() //this.isSuspend

actual fun <T : Any> proxyFor(forInterface: KClass<*>, invokeMethod: (handler:Any, proxy: Any?, callable: KCallable<*>, methodName:String, args: Array<out Any>) -> Any?): T {
    TODO()
}

actual fun Any.reflect() = ObjectReflection(this)
actual fun KClass<*>.reflect() = ClassReflection(this)

actual class ClassReflection<T : Any> actual constructor(val kclass: KClass<T>) {

    actual val isAbstract: Boolean
        get() {
            return this.kclass.simpleName!!.endsWith("Abstract")
        }

    actual val allPropertyNames: List<String> by lazy {
        val cls = this.kclass.js
        val js: Array<String> = js("""
             var p=[];
             var prt=cls.prototype; //Object.getPrototypeOf(cls);
             var nms=Object.getOwnPropertyNames(prt);
             for(var i=0; i<nms.length; i++ ) if (p.indexOf(nms[i]) == -1) p.push(nms[i])
            """)
        js.toList()
    }

    actual val allMemberFunctions: List<KFunction<*>> by lazy {
        TODO()
    }

    actual val qualifiedName:String = KotlinxReflect.qualifiedNameForClass(this.kclass)

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
        val cls = this.kclass.js
        return js("!!Object.getOwnPropertyDescriptor(cls.prototype, propertyName).set")
    }

    actual fun getProperty(self: T, propertyName: String): Any? {
       // return if(IR) {
       //     js("self['_'+propertyName]")
       // } else {
       return     js("self[propertyName]")
      //  }
    }

    actual fun setProperty(self: T, propertyName: String, value: Any?) {
      //  if(IR) {
      //      js("self['_'+propertyName] = value")
      //  } else {
            js("self[propertyName] = value")
      //  }
    }

    actual val isEnum:Boolean get() {
        val jsCls = this.kclass.js.asDynamic()
        val md = jsCls.`$metadata$`
        return md.fastPrototype == Enum::class.js.asDynamic().prototype
    }

    actual fun <E:Enum<E>> enumValues(): List<E> {
        return if (isEnum) {
            //val jsCls = this.kclass.js.asDynamic()
            //jsCls.values().unsafeCast<Array<E>>().asList()
            KotlinxReflect.enumValues(this.qualifiedName) as List<E>
        } else {
            emptyList()
        }
    }

    actual fun <E:Enum<E>> enumValueOf(name:String): E? {
        return this.enumValues().firstOrNull { it.name == name } as E?
    }

    actual fun call(self: T, methodName: String, vararg args: Any?): Any? {
 //       return if (IR) {
          return  js("self[methodName](args)")
 //       } else {
//            js("self[methodName](args)")
//        }
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
        /*
        val js: Array<String> = js("""(function(){
            var props = [];
            var proto = Object.getPrototypeOf(self);
            Object.getOwnPropertyNames(proto).forEach(function(name) {
                if (typeof self[name] === 'function') {
                    // ignore it
                } else if (self.hasOwnProperty(name) && props.indexOf(name) === -1) {
                    props.push(name);
                }
                var descriptor = Object.getOwnPropertyDescriptor(proto, name);
                if (typeof descriptor.get === 'function' && props.indexOf(name) === -1) {
                    props.push(name);
                }
            });
            Object.getOwnPropertyNames(self).forEach(function(name) {
                if (typeof self[name] === 'function') {
                    // ignore it
                } else if (self.hasOwnProperty(name) && props.indexOf(name) === -1) {
                    props.push(name);
                }
                var descriptor = Object.getOwnPropertyDescriptor(self, name);
                if (typeof descriptor.get === 'function' && props.indexOf(name) === -1) {
                    props.push(name);
                }
            });
            props
        })();""")
        */
        //FIXME: this does not seem to get all properties from a Kotlin object!!
        // tried above code but it made no difference/
        // problem seems to be getting properties that are defined using Object.defineProperty in Kotlin generated code
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
        val cls = this.self::class.js
        return js("!!Object.getOwnPropertyDescriptor(cls.prototype, propertyName).set")
    }

    actual fun getProperty(propertyName: String): Any? {
        val self = this.self //ensures self is available in the js script below
        //return if (IR) {
       //     js("self['_'+propertyName]")
        //} else {
        return    js("self[propertyName]")
        //}
//return "Reflect.get(obj, propertyName)"
    }

    actual fun setProperty(propertyName: String, value: Any?) {
        val self = this.self //ensures self is available in the js script below
        //if(IR) {
        //    js("self['_'+propertyName] = value")
        //} else {
            js("self[propertyName] = value")
        //}
    }

    actual fun call(methodName: String, vararg args: Any?): Any? {
        val self = this.self //ensures self is available in the js script below
 //       return if (IR) {
 //           js("self[methodName](args)")
 //       } else {
         return   js("self[methodName](args)")
 //       }
    }

    actual suspend fun callSuspend(methodName: String, vararg args: Any?) : Any? {
        TODO()
    }
}
