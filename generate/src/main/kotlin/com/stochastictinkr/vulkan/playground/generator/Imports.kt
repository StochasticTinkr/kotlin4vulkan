package com.stochastictinkr.vulkan.playground.generator

import kotlin.reflect.KClass

interface Imports {
    val importNames: Set<CharSequence>
    fun importNames(imports: Collection<CharSequence>)
    fun import(vararg imports: CharSequence)
    fun import(vararg imports: Class<*>)
    fun import(imports: Collection<Class<*>>)
    fun import(vararg imports: KClass<*>)

    fun mergeImports(imports: Imports) {
        importNames(imports.importNames)
    }
}

class ImportsImpl(
    private val mutableImportNames: MutableSet<String>,
) : Imports {
    override val importNames: Set<CharSequence> get() = mutableImportNames

    override fun importNames(imports: Collection<CharSequence>) {
        this.mutableImportNames += imports.map { it.toString() }
    }

    override fun import(vararg imports: CharSequence) {
        importNames(imports.toList())
    }

    override fun import(vararg imports: Class<*>) {
        import(imports.toList())
    }

    override fun import(imports: Collection<Class<*>>) {
        importNames(
            imports
                .filterNot { it.isPrimitive }
                .map { it.outerMost.kotlin.qualifiedName ?: error("Unable to import $it") })
    }

    override fun import(vararg imports: KClass<*>) {
        import(imports.map { it.java })
    }

}
