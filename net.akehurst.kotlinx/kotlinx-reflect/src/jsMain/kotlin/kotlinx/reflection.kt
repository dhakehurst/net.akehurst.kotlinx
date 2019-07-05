package net.akehurst.kotlinx.reflect

import kotlin.reflect.KClass


actual class Reflection<T : Any> actual constructor(val clazz:KClass<T>) {


    actual val isAbstract:Boolean get() {
        TODO()
    }

    actual val allPropertyNames:List<String> by lazy {
        val cls = this.clazz
        val js:Array<String> = js("Object.keys(cls)")
        js.toList()
    }

    actual fun createInstance() : T {
        TODO()
    }

    actual fun <S : Any> isSupertypeOf(subtype: KClass<S>): Boolean {
        TODO()
    }

    actual fun callProperty(propertyName:String, obj:Any) : Any? {
        return js("obj[propertyName]")
        //return "Reflect.get(obj, propertyName)"
    }


}

