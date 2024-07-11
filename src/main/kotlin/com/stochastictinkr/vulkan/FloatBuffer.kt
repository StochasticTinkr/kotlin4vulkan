package com.stochastictinkr.vulkan

import com.stochastictinkr.util.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

data object FloatBufferAllocator : CompleteAllocator<FloatBuffer> {
    override val sizeOf: Int = Float.SIZE_BYTES

    override fun malloc(count: Int, stack: MemoryStack): FloatBuffer = stack.mallocFloat(count)
    override fun calloc(count: Int, stack: MemoryStack): FloatBuffer = stack.callocFloat(count)
    override fun malloc(count: Int): FloatBuffer = MemoryUtil.memAllocFloat(count)
    override fun calloc(count: Int): FloatBuffer = MemoryUtil.memCallocFloat(count)
    override fun free(buffer: FloatBuffer) = MemoryUtil.memFree(buffer)
    override fun from(buffer: ByteBuffer): FloatBuffer = buffer.asFloatBuffer()
}

data object FloatBufferBuilder {
    context(OnStack)
    @OptIn(ExperimentalContracts::class)
    inline fun calloc(size: Int, init: FloatBuffer.() -> Unit): FloatBuffer {
        contract { callsInPlace(init, InvocationKind.EXACTLY_ONCE) }
        return stack.callocFloat(size).apply(init)
    }

    context(OnStack)
    fun floats(vararg floats: Float): FloatBuffer {
        return stack.floats(*floats)
    }
}