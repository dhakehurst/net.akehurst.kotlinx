/**
 * Copyright (C) 2022 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1


typealias EnumValuesFunction = () -> Array<Enum<*>>

/** Creates an instance from the given constructor arguments. */
typealias ConstructorInvoker = (args: List<Any?>) -> Any

/** Calls a member function on [self] with the given arguments. */
typealias FunctionInvoker = (self: Any, args: List<Any?>) -> Any?

/** Calls a suspend member function on [self] with the given arguments. */
typealias SuspendFunctionInvoker = suspend (self: Any, args: List<Any?>) -> Any?

/** Reads a property of [self]. */
typealias PropertyGetter = (self: Any) -> Any?

/** Writes a property of [self]. */
typealias PropertySetter = (self: Any, value: Any?) -> Unit

/**
 * Everything registered for one class.
 * Filled by the code that the kotlinx-reflect Gradle plugin generates (using KSP), or by hand.
 */
class RegisteredClass internal constructor(
    val qualifiedName: String,
    val kclass: KClass<*>
) {
    var isAbstract: Boolean = false; internal set
    var isInterface: Boolean = false; internal set
    var enumValuesFunction: EnumValuesFunction? = null; internal set
    var objectInstance: Any? = null; internal set

    internal val _supertypes = mutableListOf<KClass<*>>()
    internal val _properties = linkedMapOf<String, RegisteredProperty>()
    internal val _constructors = mutableMapOf<Int, ConstructorInvoker>()
    internal val _functions = linkedMapOf<String, MutableList<RegisteredFunction>>()

    /** Direct supertypes, as registered. */
    val supertypes: List<KClass<*>> get() = _supertypes

    /** Properties declared or overridden in this class, in registration order. */
    val properties: Map<String, RegisteredProperty> get() = _properties

    /** Constructors, keyed by number of parameters. */
    val constructors: Map<Int, ConstructorInvoker> get() = _constructors

    /** Member functions declared or overridden in this class, keyed by name. */
    val functions: Map<String, List<RegisteredFunction>> get() = _functions

    val isEnum: Boolean get() = null != enumValuesFunction
    val isObject: Boolean get() = null != objectInstance
}

class RegisteredProperty internal constructor(
    val name: String,
    val getter: PropertyGetter,
    /** null if the property is not mutable (or its setter is not accessible) */
    val setter: PropertySetter?
) {
    val isMutable: Boolean get() = null != setter
}

class RegisteredFunction internal constructor(
    val name: String,
    val arity: Int,
    val reference: KFunction<*>?,
    val invoker: FunctionInvoker?,
    val suspendInvoker: SuspendFunctionInvoker?
) {
    val isSuspend: Boolean get() = null != suspendInvoker
}

object KotlinxReflect {

    private val _registeredClasses = mutableMapOf<String, RegisteredClass>()
    private val _registeredClassesReverse = mutableMapOf<KClass<*>, RegisteredClass>()
    private val _registeredModules = mutableSetOf<String>()

    /** Incremented by every registration, so that lookups built from the registry can be cached. */
    internal var registrationVersion: Int = 0
        private set

    /**
     * When true, only the registered information is used, on every platform.
     * Without it, JVM uses kotlin-reflect and JS falls back to JavaScript names for anything not registered;
     * setting it makes them behave exactly like Wasm and Native, which is useful when debugging.
     * On JVM the default comes from the system property 'kotlinx.reflect.forceRegistry'; elsewhere it is false.
     * Wasm and Native always use only the registry, whatever this is set to.
     */
    var forceRegistry: Boolean = defaultForceRegistry()

    /**
     * Runs [registration] once for the module identified by [moduleId], under a lock,
     * so that concurrent callers wait until the registration is complete.
     * The module is only marked as registered when [registration] completes, so after a failure the next call tries again.
     * Used by the generated `KotlinxReflectForModule.registerUsedClasses()`.
     */
    fun registerModule(moduleId: String, registration: () -> Unit) {
        withRegistryLock {
            if (moduleId !in _registeredModules) {
                registration()
                _registeredModules.add(moduleId)
            }
        }
    }

    val registeredClasses: Map<String, KClass<*>> get() = _registeredClasses.mapValues { it.value.kclass }

    /**
     * Registers a class. Calling it again for the same class keeps what was registered before,
     * and only replaces values that are given (non-null) this time.
     */
    fun registerClass(qualifiedName: String, cls: KClass<*>, enumValuesFunction: EnumValuesFunction? = null, objectInstance: Any? = null) {
        val existing = _registeredClassesReverse[cls]
        val rc = when {
            null == existing -> RegisteredClass(qualifiedName, cls)
            existing.qualifiedName == qualifiedName -> existing
            else -> error("Class '$cls' is already registered as '${existing.qualifiedName}', cannot register it as '$qualifiedName'")
        }
        if (null != enumValuesFunction) rc.enumValuesFunction = enumValuesFunction
        if (null != objectInstance) rc.objectInstance = objectInstance
        _registeredClasses[qualifiedName] = rc
        _registeredClassesReverse[cls] = rc
        registrationVersion++
    }

    /** Registers the class modifiers and its direct supertypes. */
    fun registerClassInfo(cls: KClass<*>, isAbstract: Boolean, isInterface: Boolean, supertypes: List<KClass<*>>) {
        val rc = registered(cls)
        rc.isAbstract = isAbstract
        rc.isInterface = isInterface
        rc._supertypes.clear()
        rc._supertypes.addAll(supertypes)
        registrationVersion++
    }

    /** Registers a property declared (or overridden) in [cls], e.g. `registerProperty(Foo::class, Foo::bar)`. */
    @Suppress("UNCHECKED_CAST")
    fun registerProperty(cls: KClass<*>, property: KProperty1<*, *>) {
        val p = property as KProperty1<Any, Any?>
        val setter: PropertySetter? = (property as? KMutableProperty1<Any, Any?>)?.let { mp -> { self, value -> mp.set(self, value) } }
        registerProperty(cls, property.name, { self -> p.get(self) }, setter)
    }

    /**
     * Registers a property declared (or overridden) in [cls], with functions to read and (if mutable) write it.
     * This form also works for `object` declarations and generic classes, e.g.
     * `registerProperty(Foo::class, "bar", { self -> (self as Foo).bar }, { self, v -> (self as Foo).bar = v as String })`.
     */
    fun registerProperty(cls: KClass<*>, name: String, getter: PropertyGetter, setter: PropertySetter? = null) {
        registered(cls)._properties[name] = RegisteredProperty(name, getter, setter)
        registrationVersion++
    }

    /** Registers a constructor of [cls] that takes [arity] parameters. */
    fun registerConstructor(cls: KClass<*>, arity: Int, invoker: ConstructorInvoker) {
        registered(cls)._constructors[arity] = invoker
        registrationVersion++
    }

    /** Registers a member function of [cls] that takes [arity] parameters. */
    fun registerFunction(cls: KClass<*>, name: String, arity: Int, invoker: FunctionInvoker, reference: KFunction<*>? = null) {
        addFunction(cls, RegisteredFunction(name, arity, reference, invoker, null))
    }

    /** Registers a suspend member function of [cls] that takes [arity] parameters. */
    fun registerSuspendFunction(cls: KClass<*>, name: String, arity: Int, invoker: SuspendFunctionInvoker, reference: KFunction<*>? = null) {
        addFunction(cls, RegisteredFunction(name, arity, reference, null, invoker))
    }

    /** Whether [function] is a suspend function, if it was registered (with its reference); otherwise null. */
    fun isSuspend(function: KFunction<*>): Boolean? =
        _registeredClasses.values.firstNotNullOfOrNull { rc ->
            rc.functions[function.name]?.firstOrNull { it.reference == function }?.isSuspend
        }

    /** Everything registered for [cls], or null if it is not registered. */
    fun registeredClass(cls: KClass<*>): RegisteredClass? = _registeredClassesReverse[cls]

    fun classForName(qualifiedName: String): KClass<*> {
        return _registeredClasses[qualifiedName]?.kclass
            ?: error("Cannot find class $qualifiedName, is the class registered with KotlinxReflect and did you remember to call 'KotlinxReflectForModule.registerUsedClasses()'?")
    }

    fun qualifiedNameForClass(kclass: KClass<*>): String {
        return _registeredClassesReverse[kclass]?.qualifiedName
            ?: error("Cannot get qualifiedName of '${kclass}' is it registered with KotlinxReflect and did you remember to call 'KotlinxReflectForModule.registerUsedClasses()'?")
    }

    @Suppress("UNCHECKED_CAST")
    fun <E : Enum<E>> enumValues(qualifiedName: String): List<E> =
        _registeredClasses[qualifiedName]?.enumValuesFunction?.invoke()?.asList() as List<E>? ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> objectInstance(qualifiedName: String): T? = _registeredClasses[qualifiedName]?.objectInstance as T?

    /**
     * for debugging
     */
    val registeredClassesToString: String get() = _registeredClasses.keys.joinToString(separator = "\n") { it }

    private fun registered(cls: KClass<*>): RegisteredClass =
        _registeredClassesReverse[cls]
            ?: error("Class '$cls' must be registered with 'KotlinxReflect.registerClass' before its details are registered")

    private fun addFunction(cls: KClass<*>, fn: RegisteredFunction) {
        val list = registered(cls)._functions.getOrPut(fn.name) { mutableListOf() }
        list.removeAll { it.arity == fn.arity }
        list.add(fn)
        registrationVersion++
    }
}

/** JVM reads the 'kotlinx.reflect.forceRegistry' system property; other platforms default to false. */
internal expect fun defaultForceRegistry(): Boolean

/** Runs [block] holding the registry lock: a real lock on JVM and Native, nothing on single-threaded JS and Wasm. */
internal expect fun withRegistryLock(block: () -> Unit)
