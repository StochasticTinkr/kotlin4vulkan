import com.stochastictinkr.util.*
import com.stochastictinkr.vulkan.*
import com.stochastictinkr.vulkan.VkPhysicalDevicePropertiesBuilder.deviceType
import com.stochastictinkr.vulkan.VkQueueFamilyPropertiesBuilder.queueFlags
import kotlin.test.Test

class TryingItOut {
    @Test
    fun test() {
        useVulkanSdk()
        val layers =
            (Vulkan.enumerateInstanceLayerProperties()
                .map { it.layerNameString() })

        println("--==### Global extensions ###==--")
        Vulkan.enumerateInstanceExtensionProperties().forEach {
            println("Extension: ${it.extensionNameString()}")
        }

        layers.forEach { layerName ->
            println("--==### ${layerName ?: "Global extensions"} ###==--")
            Vulkan.enumerateInstanceExtensionProperties(layerName)
                .forEach {
                    println("Extension: ${it.extensionNameString()}")
                }
        }

        val instance = onStack {
            Vulkan.createInstance(
                VkInstanceCreateInfo(stack) {
                    pApplicationInfo(
                        VkApplicationInfo(stack) {
                            pApplicationName(stack.UTF8("Hello, Vulkan!"))
                            pEngineName(stack.UTF8("StochasticTinkr"))
                            apiVersion(Version(1, 0, 0).intValue)
                        }
                    )
                    if ("VK_LAYER_KHRONOS_validation" in Vulkan.enumerateInstanceLayerProperties()
                            .map { it.layerNameString() }
                    ) {
                        ppEnabledLayerNames(stack.utf8List("VK_LAYER_KHRONOS_validation"))
                    }
                    if ("VK_KHR_portability_enumeration" in Vulkan.enumerateInstanceExtensionProperties()
                            .map { it.extensionNameString() }
                    ) {
                        ppEnabledExtensionNames(stack.utf8List("VK_KHR_portability_enumeration"))
                        flags { ENUMERATE_PORTABILITY_KHR }
                    }
                })
        }
        instance.enumeratePhysicalDevices().forEach { physicalDevice ->
            onStack {
                val properties = physicalDevice.getProperties(stack)
                println("Physical device: ${properties.deviceNameString()}")
                println("    API version: ${Version(properties.apiVersion())}")
                println("    Driver version: ${Version(properties.driverVersion())}")
                println("    Vendor ID: 0x${properties.vendorID().toString(16)}")
                println("    Device ID: 0x${properties.deviceID().toString(16)}")
                println("    Device type: ${properties.deviceType.name}")
                println("    Limits:")
                properties.limits().let {
                    println("        Max image dimension 1D: ${it.maxImageDimension1D()}")
                    println("        Max image dimension 2D: ${it.maxImageDimension2D()}")
                    println("        Max image dimension 3D: ${it.maxImageDimension3D()}")
                    println("        Max image dimension cube: ${it.maxImageDimensionCube()}")
                    println("        Max image array layers: ${it.maxImageArrayLayers()}")
                    println("        Max texel buffer elements: ${it.maxTexelBufferElements()}")
                    println("        Max uniform buffer range: ${it.maxUniformBufferRange()}")
                    println("        Max storage buffer range: ${it.maxStorageBufferRange()}")
                    println("        Max push constants size: ${it.maxPushConstantsSize()}")
                    println("        Max memory allocation count: ${it.maxMemoryAllocationCount()}")
                    println("        Max sampler allocation count: ${it.maxSamplerAllocationCount()}")
                    println("        Buffer image granularity: ${it.bufferImageGranularity()}")
                    println("        Sparse address space size: ${it.sparseAddressSpaceSize()}")
                    println("        Max bound descriptor sets: ${it.maxBoundDescriptorSets()}")
                    println("        Max per-stage descriptor samplers: ${it.maxPerStageDescriptorSamplers()}")
                    println("        Max per-stage descriptor uniform buffers: ${it.maxPerStageDescriptorUniformBuffers()}")
                    println("        Max per-stage descriptor storage buffers: ${it.maxPerStageDescriptorStorageBuffers()}")
                    println("        Max per-stage descriptor sampled images: ${it.maxPerStageDescriptorSampledImages()}")
                    println("        Max per-stage descriptor storage images: ${it.maxPerStageDescriptorStorageImages()}")
                    println("        Max per-stage descriptor input attachments: ${it.maxPerStageDescriptorInputAttachments()}")
                    println("        Max per-stage resources: ${it.maxPerStageResources()}")
                    println("        Max descriptor set samplers: ${it.maxDescriptorSetSamplers()}")
                    println("        Max descriptor set uniform buffers: ${it.maxDescriptorSetUniformBuffers()}")
                    println("        Max descriptor set uniform buffers dynamic: ${it.maxDescriptorSetUniformBuffersDynamic()}")
                    println("        Max descriptor set storage buffers: ${it.maxDescriptorSetStorageBuffers()}")
                    println("        Max descriptor set storage buffers dynamic: ${it.maxDescriptorSetStorageBuffersDynamic()}")
                    println("        Max descriptor set sampled images: ${it.maxDescriptorSetSampledImages()}")
                    println("        Max descriptor set storage images: ${it.maxDescriptorSetStorageImages()}")
                    println("        Max descriptor set input attachments: ${it.maxDescriptorSetInputAttachments()}")
                }
                physicalDevice.getQueueFamilyProperties().use {
                    it.forEachIndexed { index, properties ->
                        println("    Queue family $index: ")
                        println("        Queue count: ${properties.queueCount()}")
                        println("        Queue flags: ${properties.queueFlags.name}")
                        println("        Timestamp valid bits: ${properties.timestampValidBits()}")
                        val minImageTransferGranularity = properties.minImageTransferGranularity()
                        println("        Min image transfer granularity: ${minImageTransferGranularity.height()}x${minImageTransferGranularity.width()}x${minImageTransferGranularity.depth()}")
                    }
                }


            }
        }
    }
}