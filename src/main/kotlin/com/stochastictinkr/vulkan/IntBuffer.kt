package com.stochastictinkr.vulkan

import com.stochastictinkr.util.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.IntBuffer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

data object IntBufferAllocator : CompleteAllocator<IntBuffer> {
    override val sizeOf: Int = Int.SIZE_BYTES

    override fun malloc(count: Int, stack: MemoryStack): IntBuffer = stack.mallocInt(count)
    override fun calloc(count: Int, stack: MemoryStack): IntBuffer = stack.callocInt(count)
    override fun malloc(count: Int): IntBuffer = MemoryUtil.memAllocInt(count)
    override fun calloc(count: Int): IntBuffer = MemoryUtil.memCallocInt(count)
    override fun free(buffer: IntBuffer) = MemoryUtil.memFree(buffer)
    override fun from(buffer: ByteBuffer): IntBuffer = buffer.asIntBuffer()
}

data object IntBufferBuilder {
    context(OnStack)
    @OptIn(ExperimentalContracts::class)
    inline fun calloc(size: Int, init: IntBuffer.() -> Unit): IntBuffer {
        contract { callsInPlace(init, InvocationKind.EXACTLY_ONCE) }
        return stack.callocInt(size).apply(init)
    }

    context(OnStack)
    fun ints(vararg ints: Int): IntBuffer {
        return stack.ints(*ints)
    }
}