package com.stochastictinkr.vulkan

import com.stochastictinkr.util.*
import org.lwjgl.vulkan.*

class DebugUtilsMessengerBuilder {
    var messageSeverity = VkDebugUtilsMessageSeverityFlagsEXT(0)
    var messageType = VkDebugUtilsMessageTypeFlagsEXT(0)
    var userCallback: VkDebugUtilsMessengerCallbackEXTI = VkDebugUtilsMessengerCallbackEXTI { _, _, _, _ -> 0 }
        private set
    var debugCreateInstance = true
    var debugInstance = true
    internal var onCreate: (VkDebugUtilsMessengerEXT) -> Unit = {}

    fun severity(builder: VkDebugUtilsMessageSeverityFlagsEXTBuilder.() -> VkDebugUtilsMessageSeverityFlagsEXT) {
        messageSeverity += VkDebugUtilsMessageSeverityFlagsEXTBuilder.builder()
    }

    fun type(builder: VkDebugUtilsMessageTypeFlagsEXTBuilder.() -> VkDebugUtilsMessageTypeFlagsEXT) {
        messageType += VkDebugUtilsMessageTypeFlagsEXTBuilder.builder()
    }

    fun callback(callback: (VkDebugUtilsMessageSeverityFlagBitsEXT, VkDebugUtilsMessageTypeFlagsEXT, VkDebugUtilsMessengerCallbackDataEXT) -> Unit) {
        userCallback = VkDebugUtilsMessengerCallbackEXTI { severity, type, data, _ ->
            callback(
                VkDebugUtilsMessageSeverityFlagBitsEXT(severity),
                VkDebugUtilsMessageTypeFlagsEXT(type),
                VkDebugUtilsMessengerCallbackDataEXT.create(data)
            )
            0 // Always return VK_FALSE, as per the spec.
        }
    }

    fun onCreate(callback: (VkDebugUtilsMessengerEXT) -> Unit) {
        onCreate = callback
    }

    operator fun invoke(configure: DebugUtilsMessengerBuilder.() -> Unit) {
        configure()
    }

    internal fun create(instance: VkInstance) {
        if (debugInstance) {
            onCreate(
                onStack {
                    instance.createDebugUtilsMessengerEXT(VkDebugUtilsMessengerCreateInfoEXT(stack) {
                        messageSeverity(messageSeverity)
                        messageType(messageType)
                        pfnUserCallback(userCallback)
                    }, null)
                }
            )
        }
    }
}

inline fun VkInstance.createDebugUtilsMessengerEXT(builder: DebugUtilsMessengerBuilder.() -> Unit): VkDebugUtilsMessengerEXT {
    DebugUtilsMessengerBuilder().apply {
        builder()
        onStack {
            return createDebugUtilsMessengerEXT(VkDebugUtilsMessengerCreateInfoEXT(stack) {
                messageSeverity(messageSeverity)
                messageType(messageType)
                pfnUserCallback(userCallback)
            }, null)
        }
    }
}