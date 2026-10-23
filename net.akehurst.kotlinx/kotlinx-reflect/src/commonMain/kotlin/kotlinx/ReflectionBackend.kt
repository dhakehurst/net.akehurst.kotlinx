/**
 * Copyright (C) 2026 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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

/**
 * The platform-specific part of reflection.
 * [ClassReflection] and [ObjectReflection] hold the shared logic and call this for the raw lookups.
 * Every backend must give the same answers for the same (registered) class; the common tests check this.
 */
internal interface ReflectionBackend {
    fun qualifiedName(kclass: KClass<*>): String
    fun isAbstract(kclass: KClass<*>): Boolean
    fun isInterface(kclass: KClass<*>): Boolean
    fun isEnum(kclass: KClass<*>): Boolean
    fun objectInstance(kclass: KClass<*>): Any?
    fun enumValues(kclass: KClass<*>): List<Enum<*>>
    fun directSupertypes(kclass: KClass<*>): List<KClass<*>>
    fun propertyNames(kclass: KClass<*>): List<String>
    fun memberFunctions(kclass: KClass<*>): List<KFunction<*>>

    /** null when the class has no property with that name */
    fun isPropertyMutable(kclass: KClass<*>, propertyName: String): Boolean?
    fun getProperty(kclass: KClass<*>, self: Any, propertyName: String): Any?
    fun setProperty(kclass: KClass<*>, self: Any, propertyName: String, value: Any?)
    fun construct(kclass: KClass<*>, args: List<Any?>): Any
    fun call(kclass: KClass<*>, self: Any, methodName: String, args: List<Any?>): Any?
    suspend fun callSuspend(kclass: KClass<*>, self: Any, methodName: String, args: List<Any?>): Any?
    fun isProxy(self: Any): Boolean
}

/** The platform's own backend, or null if the platform has none (then the registry is used). */
internal expect val platformBackend: ReflectionBackend?

internal val backend: ReflectionBackend
    get() = platformBackend.takeUnless { KotlinxReflect.forceRegistry } ?: RegistryBackend

/** Error messages shared by all backends, so failures read the same on every platform. */
internal object ReflectErrors {
    fun notRegistered(kclass: KClass<*>): Nothing =
        error("Class '$kclass' is not registered with KotlinxReflect, did you remember to call 'KotlinxReflectForModule.registerUsedClasses()'?")

    fun propertyNotFound(kclass: KClass<*>, propertyName: String): Nothing =
        error("Property '$propertyName' not found on class '${kclass.simpleName}'")

    fun propertyNotMutable(kclass: KClass<*>, propertyName: String): Nothing =
        error("Property '$propertyName' on class '${kclass.simpleName}' is not mutable")

    fun constructorNotFound(kclass: KClass<*>, arity: Int): Nothing =
        error("No constructor with $arity parameter(s) found for class '${kclass.simpleName}'")

    fun methodNotFound(kclass: KClass<*>, methodName: String, arity: Int): Nothing =
        error("Method '$methodName' with $arity parameter(s) not found on class '${kclass.simpleName}'")

    fun notSuspend(kclass: KClass<*>, methodName: String): Nothing =
        error("Method '$methodName' on class '${kclass.simpleName}' is not a suspend function, use 'call' instead of 'callSuspend'")

    fun isSuspend(kclass: KClass<*>, methodName: String): Nothing =
        error("Method '$methodName' on class '${kclass.simpleName}' is a suspend function, use 'callSuspend' instead of 'call'")
}

/** Uses only what is registered with [KotlinxReflect]. */
internal object RegistryBackend : ReflectionBackend {

    private fun info(kclass: KClass<*>): RegisteredClass =
        KotlinxReflect.registeredClass(kclass) ?: ReflectErrors.notRegistered(kclass)

    /** The registered property with this name, from the class or its registered supertypes; null if none (never throws). */
    fun registeredProperty(kclass: KClass<*>, propertyName: String): RegisteredProperty? =
        hierarchy(kclass).firstNotNullOfOrNull { it.properties[propertyName] }

    /** The registered function with this name and arity, from the class or its registered supertypes; null if none (never throws). */
    fun registeredFunction(kclass: KClass<*>, methodName: String, arity: Int): RegisteredFunction? =
        hierarchy(kclass).firstNotNullOfOrNull { rc -> rc.functions[methodName]?.firstOrNull { it.arity == arity } }

    /** The class followed by all its registered supertypes, nearest first, each once. */
    private fun hierarchy(kclass: KClass<*>): List<RegisteredClass> {
        val result = mutableListOf<RegisteredClass>()
        val seen = mutableSetOf<KClass<*>>()
        var level = listOf(kclass)
        while (level.isNotEmpty()) {
            val next = mutableListOf<KClass<*>>()
            for (k in level) {
                if (seen.add(k)) {
                    val rc = KotlinxReflect.registeredClass(k)
                    if (null != rc) {
                        result.add(rc)
                        next.addAll(rc.supertypes)
                    }
                }
            }
            level = next
        }
        return result
    }

    private fun findProperty(kclass: KClass<*>, propertyName: String): RegisteredProperty? {
        info(kclass) // fail clearly if the class itself is not registered
        return registeredProperty(kclass, propertyName)
    }

    private fun findFunction(kclass: KClass<*>, methodName: String, arity: Int): RegisteredFunction {
        info(kclass)
        return registeredFunction(kclass, methodName, arity) ?: ReflectErrors.methodNotFound(kclass, methodName, arity)
    }

    override fun qualifiedName(kclass: KClass<*>): String = info(kclass).qualifiedName
    override fun isAbstract(kclass: KClass<*>): Boolean = info(kclass).isAbstract
    override fun isInterface(kclass: KClass<*>): Boolean = info(kclass).isInterface
    override fun isEnum(kclass: KClass<*>): Boolean = info(kclass).isEnum
    override fun objectInstance(kclass: KClass<*>): Any? = info(kclass).objectInstance
    override fun enumValues(kclass: KClass<*>): List<Enum<*>> = info(kclass).enumValuesFunction?.invoke()?.asList() ?: emptyList()
    // a class that is not registered (e.g. a supertype from another module) is treated as having no supertypes
    override fun directSupertypes(kclass: KClass<*>): List<KClass<*>> = KotlinxReflect.registeredClass(kclass)?.supertypes ?: emptyList()

    override fun propertyNames(kclass: KClass<*>): List<String> {
        info(kclass)
        // supertypes first, so inherited properties come before the ones a subclass adds
        return hierarchy(kclass).reversed().flatMap { it.properties.keys }.distinct()
    }

    override fun memberFunctions(kclass: KClass<*>): List<KFunction<*>> {
        info(kclass)
        return hierarchy(kclass).reversed().flatMap { rc -> rc.functions.values.flatten().mapNotNull { it.reference } }.distinct()
    }

    override fun isPropertyMutable(kclass: KClass<*>, propertyName: String): Boolean? =
        findProperty(kclass, propertyName)?.isMutable

    override fun getProperty(kclass: KClass<*>, self: Any, propertyName: String): Any? {
        val prop = findProperty(kclass, propertyName) ?: ReflectErrors.propertyNotFound(kclass, propertyName)
        return prop.getter.invoke(self)
    }

    override fun setProperty(kclass: KClass<*>, self: Any, propertyName: String, value: Any?) {
        val prop = findProperty(kclass, propertyName) ?: ReflectErrors.propertyNotFound(kclass, propertyName)
        val setter = prop.setter ?: ReflectErrors.propertyNotMutable(kclass, propertyName)
        setter.invoke(self, value)
    }

    override fun construct(kclass: KClass<*>, args: List<Any?>): Any {
        val ctor = info(kclass).constructors[args.size] ?: ReflectErrors.constructorNotFound(kclass, args.size)
        return ctor.invoke(args)
    }

    override fun call(kclass: KClass<*>, self: Any, methodName: String, args: List<Any?>): Any? {
        val fn = findFunction(kclass, methodName, args.size)
        val invoker = fn.invoker ?: ReflectErrors.isSuspend(kclass, methodName)
        return invoker.invoke(self, args)
    }

    override suspend fun callSuspend(kclass: KClass<*>, self: Any, methodName: String, args: List<Any?>): Any? {
        val fn = findFunction(kclass, methodName, args.size)
        val invoker = fn.suspendInvoker ?: ReflectErrors.notSuspend(kclass, methodName)
        return invoker.invoke(self, args)
    }

    override fun isProxy(self: Any): Boolean = false
}
