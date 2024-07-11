package com.stochastictinkr.vulkan

import org.lwjgl.vulkan.VK10.*

@JvmInline
value class Version(val intValue: Int) {
    constructor(major: Int, minor: Int, patch: Int = 0) : this(VK_MAKE_VERSION(major, minor, patch))

    val major: Int get() = VK_VERSION_MAJOR(intValue)
    val minor: Int get() = VK_VERSION_MINOR(intValue)
    val patch: Int get() = VK_VERSION_PATCH(intValue)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        val VULKAN_1_0 = Version(1, 0)
        val VULKAN_1_1 = Version(1, 1)
        val VULKAN_1_2 = Version(1, 2)
        val VULKAN_1_3 = Version(1, 3)
    }
}
