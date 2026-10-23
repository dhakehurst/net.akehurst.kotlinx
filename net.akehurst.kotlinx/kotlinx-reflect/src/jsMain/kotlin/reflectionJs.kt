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

actual fun KFunction<*>.isSuspend(): Boolean =
    KotlinxReflect.isSuspend(this) ?: error("Function '${this.name}' is not registered with KotlinxReflect, cannot tell if it is a suspend function")

actual fun <T : Any> proxyFor(forInterface: KClass<*>, invokeMethod: (handler: Any, proxy: Any?, callable: KCallable<*>, methodName: String, args: Array<out Any>) -> Any?): T {
    TODO("JS proxyFor")
}

// JS uses the registry first and falls back to JavaScript names; set forceRegistry to use only the registry
internal actual fun defaultForceRegistry(): Boolean = false

// JS is single-threaded
internal actual fun withRegistryLock(block: () -> Unit) = block()

internal actual val platformBackend: ReflectionBackend? = JsNameFallbackBackend

/**
 * Uses what is registered with [KotlinxReflect]. When a class, property, function or constructor is not registered,
 * falls back to looking it up by its JavaScript name.
 * The fallback only finds names that are not mangled, i.e. declarations exported with
 * the 'exportPublic' plugin or @JsExport.
 */
internal object JsNameFallbackBackend : ReflectionBackend {

    private val reg = RegistryBackend

    private fun registered(kclass: KClass<*>): RegisteredClass? = KotlinxReflect.registeredClass(kclass)

    override fun qualifiedName(kclass: KClass<*>): String = reg.qualifiedName(kclass)

    override fun isAbstract(kclass: KClass<*>): Boolean = registered(kclass)?.isAbstract
        ?: kclass.simpleName?.endsWith("Abstract") ?: false // JS has no abstract flag; naming convention, as before

    @OptIn(ExperimentalStdlibApi::class)
    override fun isInterface(kclass: KClass<*>): Boolean = registered(kclass)?.isInterface ?: kclass.isInterface

    override fun isEnum(kclass: KClass<*>): Boolean = registered(kclass)?.isEnum?.takeIf { it }
        ?: jsIsEnum(kclass.js, Enum::class.js)

    // an unregistered object is recognised from its metadata, but its instance is not available
    override fun isObject(kclass: KClass<*>): Boolean = registered(kclass)?.isObject?.takeIf { it } ?: jsIsObject(kclass.js)
    override fun objectInstance(kclass: KClass<*>): Any? = registered(kclass)?.objectInstance

    override fun enumValues(kclass: KClass<*>): List<Enum<*>> = when {
        true == registered(kclass)?.isEnum -> reg.enumValues(kclass)
        else -> jsEnumValues(kclass.js)?.asList() ?: emptyList()
    }

    override fun directSupertypes(kclass: KClass<*>): List<KClass<*>> {
        val r = reg.directSupertypes(kclass)
        return when {
            r.isNotEmpty() -> r
            else -> listOfNotNull(jsSuperclass(kclass.js)?.kotlin) // superclass only, interfaces are not visible in JS
        }
    }

    // registered properties are complete (and exclude non-public ones), so JS names are only used when none are registered
    override fun propertyNames(kclass: KClass<*>): List<String> {
        val r = if (null != registered(kclass)) reg.propertyNames(kclass) else emptyList()
        return r.ifEmpty { jsAccessorNames(kclass.js, stdlibBaseClasses).asList() }
    }

    override fun memberFunctions(kclass: KClass<*>): List<KFunction<*>> =
        if (null != registered(kclass)) reg.memberFunctions(kclass) else emptyList()

    override fun isPropertyMutable(kclass: KClass<*>, propertyName: String): Boolean? = when {
        null != reg.registeredProperty(kclass, propertyName) -> reg.isPropertyMutable(kclass, propertyName)
        else -> jsIsMutable(kclass.js, propertyName)
    }

    override fun getProperty(kclass: KClass<*>, self: Any, propertyName: String): Any? = when {
        null != reg.registeredProperty(kclass, propertyName) -> reg.getProperty(kclass, self, propertyName)
        jsHas(self, propertyName) -> jsGet(self, propertyName)
        else -> ReflectErrors.propertyNotFound(kclass, propertyName)
    }

    override fun setProperty(kclass: KClass<*>, self: Any, propertyName: String, value: Any?) {
        when {
            null != reg.registeredProperty(kclass, propertyName) -> reg.setProperty(kclass, self, propertyName, value)
            !jsHas(self, propertyName) -> ReflectErrors.propertyNotFound(kclass, propertyName)
            false == jsIsMutable(kclass.js, propertyName) -> ReflectErrors.propertyNotMutable(kclass, propertyName)
            else -> jsSet(self, propertyName, value)
        }
    }

    // if any constructors are registered, use only those (so a wrong number of arguments fails as on other platforms)
    override fun construct(kclass: KClass<*>, args: List<Any?>): Any = when {
        true == registered(kclass)?.constructors?.isNotEmpty() -> reg.construct(kclass, args)
        else -> jsConstruct(kclass.js, args.toTypedArray())
    }

    override fun call(kclass: KClass<*>, self: Any, methodName: String, args: List<Any?>): Any? = when {
        reg.registeredFunctions(kclass, methodName, args.size).isNotEmpty() -> reg.call(kclass, self, methodName, args)
        jsIsFunction(self, methodName) -> jsCall(self, methodName, args.toTypedArray())
        else -> ReflectErrors.methodNotFound(kclass, methodName, args.size)
    }

    // suspend functions can only be called when registered
    override suspend fun callSuspend(kclass: KClass<*>, self: Any, methodName: String, args: List<Any?>): Any? =
        reg.callSuspend(kclass, self, methodName, args)

    override fun isProxy(self: Any): Boolean = false
}

// --- JavaScript helpers; parameters are referenced by name inside js(...) ---

/** Kotlin stdlib base classes whose properties (e.g. Enum.name, Throwable.message) are not listed, as on other platforms. */
private val stdlibBaseClasses: Array<JsClass<*>> = arrayOf(Enum::class.js, Throwable::class.js)

private fun jsIsObject(cls: JsClass<*>): Boolean =
    js("(cls.\$metadata\$ !== undefined && cls.\$metadata\$ !== null && cls.\$metadata\$.kind === 'object')") as Boolean

private fun jsIsEnum(cls: JsClass<*>, enumCls: JsClass<*>): Boolean =
    js("cls.prototype instanceof enumCls") as Boolean

private fun jsEnumValues(cls: JsClass<*>): Array<Enum<*>>? =
    js("(typeof cls.values === 'function') ? cls.values() : null").unsafeCast<Array<Enum<*>>?>()

private fun jsSuperclass(cls: JsClass<*>): JsClass<*>? =
    js("(function(){ var s = Object.getPrototypeOf(cls); return (typeof s === 'function' && s !== Function.prototype && s.prototype !== undefined) ? s : null; })()")
        .unsafeCast<JsClass<*>?>()

/*
 * Kotlin/JS compiles every property 'p' to a method 'get_p_<hash>_k$' on the class prototype,
 * and every 'var' also to 'set_p_<hash>_k$'. Exported properties are additionally plain fields
 * (or accessors, if they have a custom getter) with their real name.
 * These helpers use the generated methods, so they work whether or not the class is exported.
 */

/** The generated accessor method ('get' or 'set') for the property on the prototype chain, or null. */
private fun jsAccessorMethod(cls: JsClass<*>, kind: String, propertyName: String): String? = js(
    """
    (function(){
        var prefix = kind + '_' + propertyName + '_';
        var proto = cls.prototype;
        while (proto && proto !== Object.prototype) {
            var names = Object.getOwnPropertyNames(proto);
            for (var i = 0; i < names.length; i++) {
                var n = names[i];
                if (n.indexOf(prefix) === 0 && n.slice(-3) === '_k$' && n.slice(prefix.length, -3).indexOf('_') === -1) return n;
            }
            proto = Object.getPrototypeOf(proto);
        }
        return null;
    })()
    """
).unsafeCast<String?>()

/**
 * Names of properties on the class prototype chain, supertypes first.
 * The walk stops at the [stop] classes; when the class inherits from one of them, that class's own property names
 * (e.g. an exported enum's 'name' and 'ordinal') are left out too.
 */
private fun jsAccessorNames(cls: JsClass<*>, stop: Array<JsClass<*>>): Array<String> = js(
    """
    (function(){
        function namesOn(p) {
            var names = [];
            Object.getOwnPropertyNames(p).forEach(function(n) {
                var m = /^get_(.+)_[a-z0-9]+_k\$$/.exec(n);
                if (m) {
                    names.push(m[1]);
                } else if (n !== 'constructor') {
                    var d = Object.getOwnPropertyDescriptor(p, n);
                    if (d && typeof d.get === 'function') names.push(n);
                }
            });
            return names;
        }
        var stopProtos = stop.map(function(c) { return c.prototype; });
        var levels = [];
        var excluded = [];
        var proto = cls.prototype;
        while (proto && proto !== Object.prototype) {
            if (stopProtos.indexOf(proto) !== -1) {
                for (var sp = proto; sp && sp !== Object.prototype; sp = Object.getPrototypeOf(sp)) {
                    excluded = excluded.concat(namesOn(sp));
                }
                break;
            }
            levels.unshift(namesOn(proto));
            proto = Object.getPrototypeOf(proto);
        }
        var result = [];
        levels.forEach(function(l){ l.forEach(function(n){
            if (result.indexOf(n) === -1 && excluded.indexOf(n) === -1) result.push(n);
        }); });
        return result;
    })()
    """
).unsafeCast<Array<String>>()

/** Descriptor of an accessor property (custom getter/setter) with that name on the prototype chain, or null. */
private fun jsAccessorDescriptor(cls: JsClass<*>, propertyName: String): dynamic = js(
    """
    (function(){
        var proto = cls.prototype;
        while (proto && proto !== Object.prototype) {
            var d = Object.getOwnPropertyDescriptor(proto, propertyName);
            if (d && (typeof d.get === 'function' || typeof d.set === 'function')) return d;
            proto = Object.getPrototypeOf(proto);
        }
        return null;
    })()
    """
)

/** true if it is a 'var', false if a 'val', null if the class has no such property. */
private fun jsIsMutable(cls: JsClass<*>, propertyName: String): Boolean? {
    val d = jsAccessorDescriptor(cls, propertyName)
    return when {
        null != jsAccessorMethod(cls, "set", propertyName) -> true
        null != jsAccessorMethod(cls, "get", propertyName) -> false
        null != d -> js("typeof d.set === 'function'") as Boolean
        else -> null
    }
}

private fun jsHas(self: Any, propertyName: String): Boolean =
    null != jsAccessorMethod(self::class.js, "get", propertyName) || js("propertyName in self") as Boolean

private fun jsGet(self: Any, propertyName: String): Any? {
    val getter = jsAccessorMethod(self::class.js, "get", propertyName)
    return if (null != getter) js("self[getter]()") else js("self[propertyName]")
}

private fun jsSet(self: Any, propertyName: String, value: Any?) {
    val setter = jsAccessorMethod(self::class.js, "set", propertyName)
    if (null != setter) js("self[setter](value)") else js("self[propertyName] = value")
}
private fun jsIsFunction(self: Any, methodName: String): Boolean = js("typeof self[methodName] === 'function'") as Boolean
private fun jsCall(self: Any, methodName: String, args: Array<Any?>): Any? = js("self[methodName].apply(self, args)")
private fun jsConstruct(cls: JsClass<*>, args: Array<Any?>): Any = js("Reflect.construct(cls, args)")
