package com.stochastictinkr.vulkan.playground.generator

object LwjglClasses {
    private val classLoader: ClassLoader = this::class.java.classLoader
    private val lwjglClasses = mutableMapOf<String, Class<*>?>()
    fun system(name: String): Class<*>? = lwjglClasses.getOrPut(name) {
        val className = "org.lwjgl.system.${name.replace('.', '$')}"
        try {
            Class.forName(className, false, classLoader)
        } catch (e: ClassNotFoundException) {
            null
        }
    }
    fun vulkan(name: String): Class<*>? = lwjglClasses.getOrPut(name) {
        val className = "org.lwjgl.vulkan.${name.replace('.', '$')}"
        try {
            classLoader.loadClass(className)
        } catch (e: ClassNotFoundException) {
            null
        }
    }
}