package com.stochastictinkr.vulkan

import com.stochastictinkr.util.*
import org.lwjgl.vulkan.*

fun VkDebugReportCallbackEXT.close(instance: VkInstance) {
    EXTDebugReport.vkDestroyDebugReportCallbackEXT(instance, address, null)
    close()
}

fun VkDebugReportCallbackEXT.toAutoCloseable(instance: VkInstance) =
    AutoCloseableWrapper(this) {
        it.close(instance)
        it.close()
    }