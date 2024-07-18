package com.stochastictinkr.vulkan

import com.stochastictinkr.util.*
import com.stochastictinkr.vulkan.VkInstanceCreateInfoBuilder.flags
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@Suppress("MemberVisibilityCanBePrivate")
class InstanceBuilder @PublishedApi internal constructor() {
    @PublishedApi
    internal val configureCreateInfo =
        mutableListOf<context(VkInstanceCreateInfo, OnStack) VkInstanceCreateInfoBuilder.() -> Unit>()

    @PublishedApi
    internal val afterInstanceCreated = mutableListOf<(VkInstance) -> Unit>()

    @PublishedApi
    internal val enabledExtensions = mutableSetOf<String>()

    @PublishedApi
    internal val enabledLayers = mutableSetOf<String>()

    var flags = VkInstanceCreateFlags(0)

    val availableExtensions = Vulkan.enumerateInstanceExtensionProperties().map { it.extensionNameString() }.toSet()
    val availableLayers = Vulkan.enumerateInstanceLayerProperties().map { it.layerNameString() }.toSet()

    class ApplicationInfoBuilder internal constructor(
        var applicationName: String? = null,
        var applicationVersion: Version = Version(1, 0),
        var engineName: String? = null,
        var engineVersion: Version = Version(1, 0),
        var apiVersion: Version = Version.VULKAN_1_0,
    ) {
        operator fun invoke(
            name: String? = this.applicationName,
            applicationVersion: Version = this.applicationVersion,
            engineName: String? = this.engineName,
            engineVersion: Version = this.engineVersion,
            apiVersion: Version = this.apiVersion,
        ) {
            applicationName = name
            this.applicationVersion = applicationVersion
            this.engineName = engineName
            this.engineVersion = engineVersion
            this.apiVersion = apiVersion
        }

        operator fun invoke(configure: ApplicationInfoBuilder.() -> Unit) {
            configure()
        }
    }

    /**
     * The application information for the instance.
     */
    val application by lazy {
        ApplicationInfoBuilder().apply {
            configureCreateInfo.add {
                pApplicationInfo(VkApplicationInfo(stack) {
                    pApplicationName(applicationName?.let(stack::UTF8))
                    applicationVersion(applicationVersion.intValue)
                    pEngineName(engineName?.let(stack::UTF8))
                    engineVersion(engineVersion.intValue)
                    apiVersion(apiVersion.intValue)
                })
            }
        }
    }

    private val debugUtilsMessenger by lazy {
        DebugUtilsMessengerBuilder().also { builder ->
            configureCreateInfo.add {
                if (builder.debugCreateInstance) {
                    pNext(VkDebugUtilsMessengerCreateInfoEXT(stack) {
                        messageSeverity(builder.messageSeverity)
                        messageType(builder.messageType)
                        pfnUserCallback(builder.userCallback)
                    })
                }
            }

            afterInstanceCreated.add { instance ->
                builder.create(instance)
            }
        }
    }

    fun extension(name: String, required: Boolean = true) =
        if (name in availableExtensions) {
            enabledExtensions.add(name)
            true
        } else {
            check(!required) { "Required Extension $name is not available." }
            false
        }

    fun extensions(vararg name: String, required: Boolean = true) {
        name.forEach { extension(it, required) }
    }

    fun enableValidation(required: Boolean = false) = layer("VK_LAYER_KHRONOS_validation", required)

    fun flag(builder: (VkInstanceCreateFlagsBuilder.() -> VkInstanceCreateFlags)) {
        flags += VkInstanceCreateFlagsBuilder.builder()
    }

    fun enablePortabilityEnumeration(required: Boolean = false) =
        extension(KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME, required)
            .also { available -> if (available) flag { ENUMERATE_PORTABILITY_KHR } }

    fun layer(name: String, required: Boolean = true) =
        if (name in availableLayers) {
            enabledLayers.add(name)
            true
        } else {
            check(!required) { "Required Layer $name is not available." }
            false
        }

    fun layers(vararg name: String, required: Boolean = true) {
        name.forEach { layer(it, required) }
    }

    /**
     * Enable the GLFW required Vulkan extensions. GLFW must be initialized before calling this function.
     */
    fun enableGlfwExtensions(required: Boolean = true) {
        GLFWVulkan.glfwGetRequiredInstanceExtensions()
            ?.map(MemoryUtil::memUTF8)
            ?.forEach { extension(it, required) }
    }

    fun debugUtilsMessenger(required: Boolean = false, init: DebugUtilsMessengerBuilder.() -> Unit) {
        if (extension(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME, required)) {
            debugUtilsMessenger.init()
        }
    }

    companion object {
        @OptIn(ExperimentalContracts::class)
        inline fun instance(configure: InstanceBuilder.() -> Unit = {}): VkInstance {
            contract { callsInPlace(configure, kotlin.contracts.InvocationKind.EXACTLY_ONCE) }
            return InstanceBuilder().run {
                configure()
                onStack {
                    Vulkan.createInstance(
                        VkInstanceCreateInfo(stack).also { createInfo ->
                            createInfo
                                .ppEnabledExtensionNames(stack.utf8List(enabledExtensions))
                                .ppEnabledLayerNames(stack.utf8List(enabledLayers))
                                .flags(flags)

                            configureCreateInfo.forEach {
                                it(createInfo, this, VkInstanceCreateInfoBuilder)
                            }
                        }
                    )
                }.apply {
                    afterInstanceCreated.forEach { it(this) }
                }
            }
        }
    }
}

inline fun Vulkan.createInstance(configure: InstanceBuilder.() -> Unit = {}) = InstanceBuilder.instance(configure)

