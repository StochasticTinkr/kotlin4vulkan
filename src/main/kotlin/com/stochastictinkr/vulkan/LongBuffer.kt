package com.stochastictinkr.vulkan

import com.stochastictinkr.util.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.LongBuffer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

data object LongBufferAllocator : CompleteAllocator<LongBuffer> {
    override val sizeOf: Int = Long.SIZE_BYTES

    override fun malloc(count: Int, stack: MemoryStack): LongBuffer = stack.mallocLong(count)
    override fun calloc(count: Int, stack: MemoryStack): LongBuffer = stack.callocLong(count)
    override fun malloc(count: Int): LongBuffer = MemoryUtil.memAllocLong(count)
    override fun calloc(count: Int): LongBuffer = MemoryUtil.memCallocLong(count)
    override fun free(buffer: LongBuffer) = MemoryUtil.memFree(buffer)
    override fun from(buffer: ByteBuffer): LongBuffer = buffer.asLongBuffer()
}

data object LongBufferBuilder {
    context(OnStack)
    @OptIn(ExperimentalContracts::class)
    inline fun calloc(size: Int, init: LongBuffer.() -> Unit): LongBuffer {
        contract { callsInPlace(init, InvocationKind.EXACTLY_ONCE) }
        return stack.callocLong(size).apply(init)
    }

    context(OnStack)
    fun longs(vararg longs: Long): LongBuffer {
        return stack.longs(*longs)
    }
}