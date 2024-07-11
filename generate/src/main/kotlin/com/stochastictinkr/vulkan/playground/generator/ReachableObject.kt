package com.stochastictinkr.vulkan.playground.generator

data class ReachableObject(
    val isSelf: Boolean,
    val path: String,
    val typeName: String,
    val jvmType: Class<*>?,
) {
    fun rootedAt(root: String) = copy(
        path = if (path.startsWith("this@")) root else "$root.$path"
    )

    fun optional(): ReachableObject {
        val whenNull = when (jvmType) {
            Integer.TYPE -> " ?: 0"
            java.lang.Long.TYPE -> " ?: 0L"
            else -> ""
        }
        return copy(
            path = path.replace(".", "?.") + whenNull
        )
    }

}

private fun ReachableObject.matches(jvmType: Class<*>?, typeName: String) =
    this.jvmType == jvmType && this.typeName == typeName

private fun ReachableObject.matches(typeString: LwjglTypeString<*>) =
    matches(typeString.jvmType, typeString.type)

fun Collection<ReachableObject>.matchingOrNull(typeString: LwjglTypeString<*>) =
    firstOrNull { it.matches(typeString) }

fun Collection<ReachableObject>.matchingOrNull(jvmType: Class<*>?, typeName: String) =
    firstOrNull { it.matches(jvmType, typeName) }