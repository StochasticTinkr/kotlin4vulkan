package com.stochastictinkr.vulkan

import com.stochastictinkr.util.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.DoubleBuffer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

data object DoubleBufferAllocator : CompleteAllocator<DoubleBuffer> {
    override val sizeOf: Int = Double.SIZE_BYTES

    override fun malloc(count: Int, stack: MemoryStack): DoubleBuffer = stack.mallocDouble(count)
    override fun calloc(count: Int, stack: MemoryStack): DoubleBuffer = stack.callocDouble(count)
    override fun malloc(count: Int): DoubleBuffer = MemoryUtil.memAllocDouble(count)
    override fun calloc(count: Int): DoubleBuffer = MemoryUtil.memCallocDouble(count)
    override fun free(buffer: DoubleBuffer) = MemoryUtil.memFree(buffer)
    override fun from(buffer: ByteBuffer): DoubleBuffer = buffer.asDoubleBuffer()
}

data object DoubleBufferBuilder {
    context(OnStack)
    @OptIn(ExperimentalContracts::class)
    inline fun calloc(size: Int, init: DoubleBuffer.() -> Unit): DoubleBuffer {
        contract { callsInPlace(init, InvocationKind.EXACTLY_ONCE) }
        return stack.callocDouble(size).apply(init)
    }

    context(OnStack)
    fun doubles(vararg doubles: Double): DoubleBuffer {
        return stack.doubles(*doubles)
    }
}