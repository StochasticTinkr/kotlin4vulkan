package com.stochastictinkr.vulkan.playground.generator

import java.lang.reflect.Method
import java.lang.reflect.Modifier.STATIC
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Converts a CamelCase string to a SNAKE_CASE string.
 *
 * Examples:
 *
 * |   Input    |     Output    |
 * |------------|---------------|
 * | FooBar     | FOO_BAR       |
 * | FooBar2    | FOO_BAR_2     |
 * | FooBar2KHR | FOO_BAR_2_KHR |
 *
 */
fun String.toSnakeUpperCase(): String {
    val wordRegex = Regex("[A-Z]+[a-z]*|\\d+")
    return wordRegex.findAll(this).joinToString("_") { it.value.uppercase() }
}

/**
 * Processes each item in a sequence, printing a progress message for each item.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> Sequence<T>.processList(processingType: String, itemName: (T) -> String, process: (T) -> Unit) {
    contract {
        callsInPlace(itemName, kotlin.contracts.InvocationKind.UNKNOWN)
        callsInPlace(process, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    println("Processing ${processingType}s...")
    val list = toList()
    val sizeLength = list.size.toString().length
    list.forEachIndexed { index, item ->
        val progress = "${(index + 1).toString().padStart(sizeLength)}/${list.size}"
        println("($progress): Processing $processingType ${itemName(item)}")
        process(item)
    }
}

/**
 * Returns true if the method is static.
 */
val Method.isStatic: Boolean get() = (modifiers and STATIC) != 0

/**
 * Returns the name of the class without package, but with the outer class if it is a nested class.
 */
val Class<*>.fullName: String
    get() = declaringClass
        ?.let { outer -> "${outer.fullName}.${kotlin.simpleName}" }
        ?: kotlin.simpleName ?: simpleName

/**
 * Find the outermost class of a class, i.e. the top level class if it is a nested class.
 * For example, for `Outer.Inner` it returns `Outer`.
 * For `Outer` it returns `Outer`.
 * For `Outer[]` it returns `Outer`.
 * For `Outer.Inner[]` it returns `Outer`.
 *
 * This is useful for getting the import string of a class.
 */
val Class<*>.outerMost: Class<*>
    get() {
        var current = this
        while (current.isArray) {
            current = current.componentType
        }
        while (current.declaringClass != null) {
            current = current.declaringClass
        }
        return current
    }

private val methodsCache = mutableMapOf<Class<*>, List<Method>>()

/**
 * Find a method by name and parameter types, whether it is static, and optionally the return type.
 *
 * @param name The name of the method.
 * @param parameterTypes The types of the parameters.
 * @param isStatic Whether the method is static.
 * @param returns The return type of the method.
 * @return The method if found, or null if not found.
 */
fun Class<*>.method(
    name: String,
    vararg parameterTypes: Class<*>,
    isStatic: Boolean = false,
    returns: Class<*>? = null,
) = methodsCache.getOrPut(this) { methods.toList() }
    .singleOrNull {
        it.name == name &&
                isStatic == it.isStatic &&
                it.parameters.size == parameterTypes.size &&
                (returns == null || it.returnType == returns) &&
                it.parameters
                    .zip(parameterTypes)
                    .all { (p, t) -> p.type == t }
    }
