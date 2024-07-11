package com.stochastictinkr.vulkan

import com.stochastictinkr.util.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

data object ByteBufferAllocator : CompleteAllocator<ByteBuffer> {
    override val sizeOf: Int = Byte.SIZE_BYTES

    override fun malloc(count: Int, stack: MemoryStack): ByteBuffer = stack.malloc(count)
    override fun calloc(count: Int, stack: MemoryStack): ByteBuffer = stack.calloc(count)
    override fun malloc(count: Int): ByteBuffer = MemoryUtil.memAlloc(count)
    override fun calloc(count: Int): ByteBuffer = MemoryUtil.memCalloc(count)
    override fun free(buffer: ByteBuffer) = MemoryUtil.memFree(buffer)
    override fun from(buffer: ByteBuffer): ByteBuffer = buffer
}

data object ByteBufferBuilder {
    context(OnStack)
    @OptIn(ExperimentalContracts::class)
    inline fun calloc(size: Int, init: ByteBuffer.() -> Unit): ByteBuffer {
        contract { callsInPlace(init, InvocationKind.EXACTLY_ONCE) }
        return stack.calloc(size).apply(init)
    }

    context(OnStack)
    fun utf8(str: String): ByteBuffer {
        return stack.UTF8(str)
    }

    context(OnStack)
    fun bytes(vararg bytes: Byte): ByteBuffer {
        return stack.bytes(*bytes)
    }
}

