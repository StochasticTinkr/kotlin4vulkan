package com.stochastictinkr.vulkan.playground.generator

fun KotlinFileBuilder.usesContracts() {
    import("kotlin.contracts.*")
    +"@OptIn(ExperimentalContracts::class)"
}