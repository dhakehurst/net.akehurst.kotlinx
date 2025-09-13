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

import net.akehurst.kotlinx.reflect.ClassUtilities.isAssignableFromKClass
import net.akehurst.kotlinx.reflect.ClassUtilities.isAssignableFromKType
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.memberFunctions

/**
 * Finds methods and constructors that can be invoked reflectively.
 * Attempts to address some of the limitations of the JDK's
 * Class.getMethod() and Class.getConstructor(), and other JDK
 * reflective facilities.
 */
class BetterMethodFinder<T : Any>(
    /**
     * The target class to look for methods and constructors in.
     */
    private val clazz: KClass<T>
) {

    companion object {
        /**
         * @param  args  an Object array
         * @return  an array of Class objects representing the classes of the
         * objects in the given Object array.  If args is null, a zero-length
         * Class array is returned.  If an element in args is null, then
         * Void.TYPE is the corresponding Class in the return array.
         */
        fun getParameterTypesFromArguments(args: List<Any?>): List<KClass<*>?> = args.map { it?.let { it::class } }

        /**
         * @param  classNames  String array of fully qualified names
         * (FQNs) of classes or primitives.  Represent an array type by using
         * its JVM type descriptor, with dots instead of slashes (e.g.
         * represent the type int[] with "[I", and Object[][] with
         * "[[Ljava.lang.Object;").
         * @return  an array of Class objects representing the classes or
         * primitives named by the FQNs in the given String array.  If the
         * String array is null, a zero-length Class array is returned.  If an
         * element in classNames is null, the empty string, "void", or "null",
         * then Void.TYPE is the corresponding Class in the return array.
         * If any classes require loading because of this operation, the
         * loading is done by the ClassLoader that loaded this class.  Such
         * classes are not initialized, however.
         * @exception  ClassNotFoundException  if any of the FQNs name
         * an unknown class
         */
        fun getParameterTypesFromQualifiedNames(vararg classNames: String?) = getParameterTypesFromQualifiedNames2(BetterMethodFinder::class.java.getClassLoader(), *classNames)

        /**
         * @param  classNames  String array of fully qualified names
         * (FQNs) of classes or primitives.  Represent an array type by using
         * its JVM type descriptor, with dots instead of slashes (e.g.
         * represent the type int[] with "[I", and Object[][] with
         * "[[Ljava.lang.Object;").
         * @param  loader  a ClassLoader
         * @return  an array of Class objects representing the classes or
         * primitives named by the FQNs in the given String array.  If the
         * String array is null, a zero-length Class array is returned.  If an
         * element in classNames is null, the empty string, "void", or "null",
         * then Void.TYPE is the corresponding Class in the return array.
         * If any classes require loading because of this operation, the
         * loading is done by the given ClassLoader.  Such classes are not
         * initialized, however.
         * @exception  ClassNotFoundException  if any of the FQNs name
         * an unknown class
         */
        fun getParameterTypesFromQualifiedNames2(loader: java.lang.ClassLoader?, vararg classNames: String?): Array<KClass<*>?> {
            val types = arrayOfNulls<KClass<*>>(classNames.size)
            for (i in 0 until classNames.size) types[i] = ClassUtilities.classForNameOrPrimitive(classNames[i], loader)
            return types
        }
    }

    /**
     * Mapping from method name to the Methods in the target
     * class with that name.
     */
    private val methodMap: MutableMap<String, MutableList<KFunction<*>>> = mutableMapOf()

    /**
     * List of the Constructors in the target class.
     */
    private val ctorList: MutableList<KFunction<*>> = mutableListOf()

    /**
     * Mapping from a Constructor or Method object to the Class
     * objects representing its formal parameters.
     */
    private val paramMap: MutableMap<KFunction<*>, List<KType>> = mutableMapOf()


    /**
     * @param  clazz  Class in which I will look for methods and
     * constructors
     * @exception  IllegalArgumentException  if clazz is null, or
     * represents a primitive, or represents an array type
     */
    init {
        if (clazz.java.isPrimitive) {
            throw IllegalArgumentException("primitive Class parameter")
        }
        if (clazz.java.isArray) {
            throw IllegalArgumentException("array Class parameter")
        }
        loadMethods()
        loadConstructors()
    }

    /**
     * Loads up the data structures for my target class's constructors.
     */
    private fun loadConstructors() {
        val ctors = clazz.constructors.toList()
        for (i in ctors.indices) {
            ctorList.add(ctors[i])
            paramMap[ctors.get(i)] = ctors.get(i).parameters.map { it.type }
        }
    }

    /**
     * Loads up the data structures for my target class's methods.
     */
    private fun loadMethods() {
        val methods = clazz.memberFunctions.toList()
        for (i in methods.indices) {
            var m = methods[i]
            val methodName = m.name
            val paramTypes = m.parameters.map { it.type }
            var list = methodMap[methodName]
            if (list == null) {
                list = mutableListOf<KFunction<*>>()
                methodMap[methodName] = list
            }
            val am = if (!ClassUtilities.classIsAccessible(clazz)) {
                ClassUtilities.getAccessibleMethodFrom(clazz, methodName, paramTypes)
            } else {
                m
            }
            if (null != am) {
                list.add(am)
                paramMap[am] = paramTypes
            }
        }
    }

    /**
     * Returns the most specific public constructor in my target class
     * that accepts the number and type of parameters in the given
     * Class array in a reflective invocation.
     *
     *
     * A null value or Void.TYPE in parameterTypes matches a
     * corresponding Object or array reference in a constructor's formal
     * parameter list, but not a primitive formal parameter.
     *
     * @param  parameterTypes  array representing the number and
     * types of parameters to look for in the constructor's signature.  A
     * null array is treated as a zero-length array.
     * @return  Constructor object satisfying the conditions
     * @exception  NoSuchMethodException  if no constructors match
     * the criteria, or if the reflective call is ambiguous based on the
     * parameter types
     */
    fun <T> findConstructorFromParameterTypes(vararg parameterTypes: KClass<*>?): KFunction<T> = findConstructorFromParameterTypes(parameterTypes.toList())
    fun <T> findConstructorFromParameterTypes(parameterTypes: List<KClass<*>?>): KFunction<T> = findMemberIn(ctorList, parameterTypes) as KFunction<T>

    fun <T> findConstructorFromArguments(vararg parameterValues: Any?): KFunction<T> = findConstructorFromArguments<T>(parameterValues.toList())
    fun <T> findConstructorFromArguments(parameterValues: List<Any?>): KFunction<T> = findConstructorFromParameterTypes<T>(getParameterTypesFromArguments(parameterValues))

    /**
     * Basis of findConstructor() and findMethod().  The member list
     * fed to this method will be either all Constructor objects or all
     * Method objects.
     */
    private fun findMemberIn(memberList: List<KFunction<*>>, parameterTypes: List<KClass<*>?>): KFunction<*> {
        val matchingMembers = mutableListOf<KFunction<*>>()
        for (member: KFunction<*> in memberList) {
            val methodParamTypes = paramMap[member] ?: error("No such member ${member}")
            if (methodParamTypes == parameterTypes) return member
            if (methodParamTypes.isAssignableFromKClass(parameterTypes)) matchingMembers.add(member)
        }
        return when (matchingMembers.size) {
            0 -> error("no member in ${clazz.simpleName} matching args ${parameterTypes.toList()}")
            1 -> matchingMembers.get(0)
            else -> findMostSpecificMemberIn(matchingMembers)
        }
    }

    /**
     * Returns the most specific public method in my target class that
     * has the given name and accepts the number and type of
     * parameters in the given Class array in a reflective invocation.
     *
     *
     * A null value or Void.TYPE in parameterTypes will match a
     * corresponding Object or array reference in a method's formal
     * parameter list, but not a primitive formal parameter.
     *
     * @param  methodName  name of the method to search for
     * @param  parameterTypes  array representing the number and
     * types of parameters to look for in the method's signature.  A
     * null array is treated as a zero-length array.
     * @return  Method object satisfying the conditions
     * @exception  NoSuchMethodException  if no methods match the
     * criteria, or if the reflective call is ambiguous based on the
     * parameter types, or if methodName is null
     */
    fun findMethodFromParameterTypes(methodName: String, vararg parameterTypes: KClass<*>?) = findMethodFromParameterTypes(methodName, parameterTypes.toList())
    fun findMethodFromParameterTypes(methodName: String, parameterTypes: List<KClass<*>?>): KFunction<*> {
        val methodList = methodMap[methodName] ?: throw java.lang.NoSuchMethodException("no method named ${clazz.simpleName}.${methodName}")
        return findMemberIn(methodList, listOf(this.clazz)+ parameterTypes)
    }

    fun findMethodFromArguments(methodName: String, vararg parameterValues: Any?) = findMethodFromArguments(methodName, parameterValues.toList())
    fun findMethodFromArguments(methodName: String, parameterValues: List<Any?>) = findMethodFromParameterTypes(methodName, getParameterTypesFromArguments(parameterValues))

    /**
     * @param  a List of Members (either all Constructors or all
     * Methods)
     * @return  the most specific of all Members in the list
     * @exception  NoSuchMethodException  if there is an ambiguity
     * as to which is most specific
     */
    private fun findMostSpecificMemberIn(memberList: List<KFunction<*>>): KFunction<*> {
        val mostSpecificMembers = mutableListOf<KFunction<*>>()
        val memberIt = memberList.iterator()
        while (memberIt.hasNext()) {
            val member = memberIt.next()
            if (mostSpecificMembers.isEmpty()) {
                // First guy in is the most specific so far.
                mostSpecificMembers.add(member)
            } else {
                var moreSpecific = true
                var lessSpecific = false

                // Is member more specific than everyone in the most-specific set?
                val specificIt = mostSpecificMembers.iterator()
                while (specificIt.hasNext()) {
                    val moreSpecificMember = specificIt.next()
                    if (!memberIsMoreSpecific(member, moreSpecificMember)) {
                        /* Can't be more specific than the whole set.  Bail out, and
               mark whether member is less specific than the member
               under consideration.  If it is less specific, it need not be
               added to the ambiguity set.  This is no guarantee of not
               getting added to the ambiguity set...we're just not clever
               enough yet to make that assessment. */
                        moreSpecific = false
                        lessSpecific = memberIsMoreSpecific(moreSpecificMember, member)
                        break
                    }
                }
                if (moreSpecific) {
                    // Member is the most specific now.
                    mostSpecificMembers.clear()
                    mostSpecificMembers.add(member)
                } else if (!lessSpecific) {
                    // Add to ambiguity set if mutually unspecific.
                    mostSpecificMembers.add(member)
                }
            }
        }
        if (mostSpecificMembers.size > 1) {
            throw java.lang.NoSuchMethodException("Ambiguous request for member in ${clazz.simpleName} matching given args")
        }
        return mostSpecificMembers[0]
    }

    /**
     * @param  first  a Member
     * @param  second  a Member
     * @return  true if the first Member is more specific than the second,
     * false otherwise.  Specificity is determined according to the
     * procedure in the Java Language Specification, section 15.12.2.
     */
    private fun memberIsMoreSpecific(first: KFunction<*>, second: KFunction<*>): Boolean {
        val firstParamTypes = paramMap[first]!!
        val secondParamTypes = paramMap[second]!!
        return firstParamTypes.isAssignableFromKType(secondParamTypes)
    }

    override fun hashCode(): Int = clazz.hashCode()

    override fun equals(other: Any?): Boolean = when (other) {
        !is BetterMethodFinder<*> -> false
        else -> this.clazz == other.clazz
    }

}
