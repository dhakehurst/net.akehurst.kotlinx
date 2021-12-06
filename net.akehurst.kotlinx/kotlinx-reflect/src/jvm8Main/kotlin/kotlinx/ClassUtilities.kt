/*
 * Copyright (C) 2021 Dr. David H. Akehurst (http://dr.david.h.akehurst.net)
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
 *
 * Based on Original from [http://adtmag.com/articles/2001/07/24/bettermethodfinderjava.aspx]
 * Original license- public domain? code published in article
 * Original Author:  Paul Holser
 * Original Date: 07/24/2001
 *
 */
package net.akehurst.kotlinx.reflect

import kotlin.reflect.*
import kotlin.reflect.full.*

/**
 * Utility routines for querying Class objects.
 */
internal object ClassUtilities {

    /**
     * Mapping from primitive wrapper Classes to their
     * corresponding primitive Classes.
     */
    private val objectToPrimitiveMap: Map<KClass<*>, KClass<*>> = mapOf(
        Boolean::class to java.lang.Boolean.TYPE.kotlin,
        Byte::class to java.lang.Byte.TYPE.kotlin,
        Char::class to java.lang.Character.TYPE.kotlin,
        Double::class to java.lang.Double.TYPE.kotlin,
        Float::class to java.lang.Float.TYPE.kotlin,
        Int::class to java.lang.Integer.TYPE.kotlin,
        Long::class to java.lang.Long.TYPE.kotlin,
        Short::class to java.lang.Short.TYPE.kotlin,
    )

    /**
     * @param  aClass  a Class
     * @return  the class's primitive equivalent, if aClass is a
     * primitive wrapper.  If aClass is primitive, returns aClass.
     * Otherwise, returns null.
     */
    fun primitiveEquivalentOf(aClass: KClass<*>?): KClass<*>? {
        return when {
            null==aClass -> null
            aClass.java.isPrimitive() -> aClass
            else -> objectToPrimitiveMap[aClass]
        }
    }

    /**
     * Mapping from primitive wrapper Classes to the sets of
     * primitive classes whose instances can be assigned an
     * instance of the first.
     */
    private val primitiveWideningsMap: Map<KClass<*>, Set<KClass<*>>> = mapOf(
        Byte::class to setOf(Short::class, Int::class, Long::class, Float::class, Double::class),
        Short::class to setOf(Int::class, Long::class, Float::class, Double::class),
        Char::class to setOf(Int::class, Long::class, Float::class, Double::class),
        Int::class to setOf(Long::class, Float::class, Double::class),
        Long::class to setOf(Float::class, Double::class),
        Float::class to setOf(Double::class),
    )

    /**
     * Tells whether an instance of the primitive class
     * represented by 'rhs' can be assigned to an instance of the
     * primitive class represented by 'lhs'.
     *
     * @param  lhs  assignee class
     * @param  rhs  assigned class
     * @return  true if compatible, false otherwise.  If either
     * argument is `null`, or one of the parameters
     * does not represent a primitive (e.g. Byte.TYPE), returns
     * false.
     */
    fun primitiveIsAssignableFrom(lhs: KClass<*>?, rhs: KClass<*>?): Boolean {
        if (lhs == null || rhs == null) return false
        if (!(lhs.java.isPrimitive() && rhs.java.isPrimitive())) return false
        if (lhs == rhs) return true
        val wideningSet = primitiveWideningsMap[rhs] as Set<*>? ?: return false
        return wideningSet.contains(lhs)
    }

    /**
     * @param  name  FQN of a class, or the name of a primitive type
     * @param  loader  a ClassLoader
     * @return  the Class for the name given.  Primitive types are
     * converted to their particular Class object.  null, the empty string,
     * "null", and "void" yield Void.TYPE.  If any classes require
     * loading because of this operation, the loading is done by the
     * given class loader.  Such classes are not initialized, however.
     * @exception  ClassNotFoundException  if name names an
     * unknown class or primitive
     */
    fun classForNameOrPrimitive(name: String?, loader: java.lang.ClassLoader?): KClass<*> {
        if (name == null || name == "" || name == "null" || name == "void") return java.lang.Void.TYPE.kotlin
        if (name == "boolean") return java.lang.Boolean.TYPE.kotlin
        if (name == "byte") return java.lang.Byte.TYPE.kotlin
        if (name == "char") return java.lang.Character.TYPE.kotlin
        if (name == "double") return java.lang.Double.TYPE.kotlin
        if (name == "float") return java.lang.Float.TYPE.kotlin
        if (name == "int") return java.lang.Integer.TYPE.kotlin
        if (name == "long") return java.lang.Long.TYPE.kotlin
        if (name == "short") return java.lang.Short.TYPE.kotlin
        return java.lang.Class.forName(name, false, loader).kotlin
    }

    /**
     * @param  aClass  a Class
     * @return  true if the class is accessible, false otherwise.
     * Presently returns true if the class is declared public.
     */
    fun classIsAccessible(aClass: KClass<*>): Boolean {
        return aClass.visibility == KVisibility.PUBLIC
    }

    /**
     * Tells whether instances of the classes in the 'rhs' array
     * could be used as parameters to a reflective method
     * invocation whose parameter list has types denoted by the
     * 'lhs' array.
     *
     * @param  lhs  Class array representing the types of the
     * formal parameters of a method
     * @param  rhs  Class array representing the types of the
     * actual parameters of a method.  A null value or
     * Void.TYPE is considered to match a corresponding
     * Object or array class in lhs, but not a primitive.
     * @return  true if compatible, false otherwise
     */
    fun compatibleClasses2(lhs: List<KClass<*>>, rhs: List<KClass<*>?>): Boolean {
        var il = 0
        var ir = 0
        while (il < lhs.size && ir < rhs.size) {
            if (rhs[ir] == null || rhs[ir] == java.lang.Void.TYPE) {
                if (lhs[il].java.isPrimitive()) return false
                ++il
                ++ir
            } else if (lhs[il].java.isArray()) {
                val lhsArrCls = lhs[il].java.getComponentType()
                while (ir < rhs.size && lhsArrCls.isAssignableFrom(rhs[ir]?.java)) {
                    ++ir
                }
                ++il
                if (ir >= rhs.size) break
            } else if (lhs[il].java.isAssignableFrom(rhs[ir]?.java)) {
                ++il
                ++ir
            } else {
                val lhsPrimEquiv = primitiveEquivalentOf(lhs[il])
                val rhsPrimEquiv = primitiveEquivalentOf(rhs[ir])
                if (!primitiveIsAssignableFrom(lhsPrimEquiv, rhsPrimEquiv)) return false
                ++il
                ++ir
            }
        }
        return il == lhs.size && ir == rhs.size
    }

    fun compatibleClasses(lhs: List<KClass<*>>, rhs: List<KClass<*>?>): Boolean {
        if (lhs.size != rhs.size) {
            return compatibleClasses2(lhs, rhs)
        }
        for (i in lhs.indices) {
            val l = lhs[i]
            val r = rhs[i]
            if (r == null || r == java.lang.Void.TYPE) {
                return if (l.java.isPrimitive) false else continue
            }
            if (!l.isSuperclassOf(r)) {
                val lhsPrimEquiv = primitiveEquivalentOf(l)
                val rhsPrimEquiv = primitiveEquivalentOf(r)
                if (!primitiveIsAssignableFrom(lhsPrimEquiv, rhsPrimEquiv)) return false
            }
        }
        return true
    }

    fun List<KType>.isAssignableFromKClass(rhs: List<KClass<*>?>): Boolean {
        if (this.size != rhs.size) error("Incompatable sizes")
        for (i in this.indices) {
            val l = this[i]
            val r = rhs[i] ?: continue //allow null to match anything
            if (l.isSupertypeOf(r.starProjectedType).not()) {
                return false
            }
        }
        return true
    }

    fun List<KType>.isAssignableFromKType(rhs: List<KType?>): Boolean {
        if (this.size != rhs.size) error("Incompatable sizes")
        for (i in this.indices) {
            val l = this[i]
            val r = rhs[i] ?: continue //allow null to match anything
            if (l.isSupertypeOf(r).not()) {
                return false
            }
        }
        return true
    }

    fun List<KParameter>.allTypesAre(types: List<KType>): Boolean {
        return when {
            types.size != this.size -> false
            else -> {
                for (i in this.indices) {
                    if (types[i] == this[i].type) {
                        continue
                    } else {
                        return false
                    }
                }
                true
            }
        }
    }

    fun KClass<*>.firstOrNullKFunction(name: String, types: List<KType>) = this.functions.firstOrNull { m ->
        m.name == name && m.parameters.allTypesAre(types)
    }

    /**
     * @param  aClass  a Class
     * @param  methodName  name of a method
     * @param  paramTypes  Class array representing the types of a method's formal parameters
     * @return  the Method with the given name and formal
     * parameter types that is in the nearest accessible class in the
     * class hierarchy, starting with aClass's superclass.  The
     * superclass and implemented interfaces of aClass are
     * searched, then their superclasses, etc. until a method is
     * found.  Returns null if there is no such method.
     */
    fun getAccessibleMethodFrom(aClass: KClass<*>, methodName: String, parameterTypes: List<KType>): KFunction<*>? {
        // Look for overridden method in the superclass/superinterfaces.
        var overriddenMethod: KFunction<*>? = null
        for (sc in aClass.superclasses) {
            overriddenMethod = sc.firstOrNullKFunction(methodName, parameterTypes)
            if (overriddenMethod != null) return overriddenMethod
        }
        // Try superclass's superclass and interfaces.
        for (sc in aClass.superclasses) {
            overriddenMethod = getAccessibleMethodFrom(sc, methodName, parameterTypes)
            if (overriddenMethod != null) return overriddenMethod
        }
        // Give up.
        return null
    }


}