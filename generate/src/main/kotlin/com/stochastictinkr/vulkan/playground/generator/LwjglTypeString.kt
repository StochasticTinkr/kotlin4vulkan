package com.stochastictinkr.vulkan.playground.generator

import org.lwjgl.system.NativeType
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import javax.annotation.Nullable


interface LwjglTypeString<E> : TypeString {
    val jvmType: Class<*>
    val element: E
    override val typeString: String
    override val type: String
    override val name: String
    val nativeType: NativeType?
    val isNullable: Boolean
}

inline fun <reified T : Annotation> AnnotatedElement.annotation(): T? = getAnnotation(T::class.java)

class MethodTypeString(override val element: Method) : LwjglTypeString<Method> {
    override val jvmType: Class<*> = element.returnType
    override val name: String = element.name
    val parameters = element.parameters.map { it.toTypeString() }
    override val nativeType = element.annotation<NativeType>()
    override val typeString: String = nativeType?.value?.substringAfter(' ') ?: jvmType.outerMost.simpleName
    override val type: String = nativeType?.value?.substringBefore(' ') ?: jvmType.outerMost.simpleName
    override val isNullable = element.annotation<Nullable>() != null
}

class ParameterTypeString(override val element: Parameter) : LwjglTypeString<Parameter> {
    override val jvmType: Class<*> = element.type
    override val name: String = element.name
    override val nativeType = element.annotation<NativeType>()
    override val typeString: String = nativeType?.value?.substringAfter(' ') ?: jvmType.outerMost.simpleName
    override val type: String =
        nativeType?.value?.removePrefix("struct ")?.substringBefore(' ') ?: jvmType.outerMost.simpleName
    override val isNullable = element.annotation<Nullable>() != null

    override fun toString(): String {
        return "ParameterTypeString(element=$element, jvmType=$jvmType, name=$name, nativeType=$nativeType, typeString='$typeString', type='$type', isNullable=$isNullable)"
    }
}

fun Method.toTypeString() = MethodTypeString(this)
fun Parameter.toTypeString() = ParameterTypeString(this)

private val kotlinKeywords =
    setOf("in", "object", "is", "as", "typealias", "by", "fun", "var", "val", "return", "this", "class")

class MatchedParameter(
    declaredParam: Param,
    val methodParam: ParameterTypeString,
) : LwjglTypeString<Parameter> by methodParam {
    override val name = declaredParam.run { if (name in kotlinKeywords) "`$name`" else name }
    override val typeString = declaredParam.typeString
    override val optional: List<Boolean> = declaredParam.optional
    val len = declaredParam.len
}