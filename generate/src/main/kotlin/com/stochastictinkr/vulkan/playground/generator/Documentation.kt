package com.stochastictinkr.vulkan.playground.generator

import java.nio.file.Path

interface Documentation {
    context(KotlinFileBuilder) fun globalObject(handleClassName: String)
    context(KotlinFileBuilder) fun handleClass(type: Type)
    context(KotlinFileBuilder) fun command(methodBuilder: CommandMethodBuilder)

}

data object EmptyDocumentation : Documentation {
    context(KotlinFileBuilder) override fun globalObject(handleClassName: String) = kDoc {
        +"The global object for the Vulkan API. This object contains all of the Vulkan functions that"
        +"don't belong to a specific handle."
    }

    context(KotlinFileBuilder) override fun handleClass(type: Type) = kDoc {
        +"A ${type.name} handle"
    }

    context(KotlinFileBuilder) override fun command(methodBuilder: CommandMethodBuilder) = kDoc {
        +methodBuilder.methodName
    }
}

fun parseDocumentation(rootPath: Path): Documentation {
    return EmptyDocumentation
}