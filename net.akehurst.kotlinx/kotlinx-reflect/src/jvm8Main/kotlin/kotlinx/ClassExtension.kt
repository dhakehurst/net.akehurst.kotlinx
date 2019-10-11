package net.akehurst.kotlinx.reflect

import java.lang.reflect.Method
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

fun < T : Any> defaultValue(): T {
    return castNull()
}

fun <T : Any> createInstance(kClass: KClass<T>): T {
    return castNull()
}

/**
 * from [https://github.com/nhaarman/mockito-kotlin/blob/2.x/mockito-kotlin/src/main/kotlin/com/nhaarman/mockitokotlin2/internal/CreateInstance.kt]
 * Uses a quirk in the bytecode generated by Kotlin
 * to cast [null] to a non-null type.
 *
 * See https://youtrack.jetbrains.com/issue/KT-8135.
 */
@Suppress("UNCHECKED_CAST")
private fun <T> castNull(): T = null as T

fun <R> methodLiteral(call:KCallable<R>) : Method? {
    return when(call) {
        is KFunction<R> -> call.javaMethod
        else -> null
    }
}