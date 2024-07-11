package com.stochastictinkr.vulkan

import org.lwjgl.system.Configuration

/**
 * Tells LWJGL to use the Vulkan SDK for loading the Vulkan library if the VULKAN_SDK environment
 * variable is set.
 *
 * For macOS, if you want to use validation layers, it is necessary to install and use the Vulkan SDK.
 */
fun useVulkanSdk() {
    System.getenv("VULKAN_SDK")?.let { vulkanSdk ->
        Configuration.VULKAN_LIBRARY_NAME.set("$vulkanSdk/lib/libvulkan.1.dylib")
    }
}
