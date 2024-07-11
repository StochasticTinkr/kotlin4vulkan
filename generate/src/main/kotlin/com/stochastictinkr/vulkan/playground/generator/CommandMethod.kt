package com.stochastictinkr.vulkan.playground.generator

/**
 * A Vulkan command and the LWJGL method that implements it.
 */
data class CommandMethod(
    val declaration: CommandDeclaration,
    val methodTypeString: MethodTypeString,
    val parameters: List<MatchedParameter>,
    val autoSizedParams: Set<String>,
) {
    val name = methodTypeString.name
}