package com.stochastictinkr.vulkan

import com.stochastictinkr.util.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

data object ShortBufferAllocator : CompleteAllocator<ShortBuffer> {
    override val sizeOf: Int = Short.SIZE_BYTES

    override fun malloc(count: Int, stack: MemoryStack): ShortBuffer = stack.mallocShort(count)
    override fun calloc(count: Int, stack: MemoryStack): ShortBuffer = stack.callocShort(count)
    override fun malloc(count: Int): ShortBuffer = MemoryUtil.memAllocShort(count)
    override fun calloc(count: Int): ShortBuffer = MemoryUtil.memCallocShort(count)
    override fun free(buffer: ShortBuffer) = MemoryUtil.memFree(buffer)
    override fun from(buffer: ByteBuffer): ShortBuffer = buffer.asShortBuffer()
}

data object ShortBufferBuilder {
    context(OnStack)
    @OptIn(ExperimentalContracts::class)
    inline fun calloc(size: Int, init: ShortBuffer.() -> Unit): ShortBuffer {
        contract { callsInPlace(init, InvocationKind.EXACTLY_ONCE) }
        return stack.callocShort(size).apply(init)
    }

    context(OnStack)
    fun shorts(vararg shorts: Short): ShortBuffer {
        return stack.shorts(*shorts)
    }
}