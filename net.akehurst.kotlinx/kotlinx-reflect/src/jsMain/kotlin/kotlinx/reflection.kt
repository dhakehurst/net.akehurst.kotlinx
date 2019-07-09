package net.akehurst.kotlinx.reflect

import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1

actual object ModuleRegistry {

    val modules = mutableSetOf<Any>()

    fun register(module:Any) {
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
                getJsOb(names.take(1),child)
            }
        }
    }

    actual fun classForName(qualifiedName:String) :KClass<*> {
        val path = qualifiedName.split(".")
        modules.forEach {
            val cls = getJsOb(path, it)
            if (null!=cls) {
                return cls as KClass<*>
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

