package com.stochastictinkr.vulkan

import com.stochastictinkr.util.*
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

data object PointerBufferAllocator : CompleteAllocator<PointerBuffer> {
    override val sizeOf: Int = PointerBuffer.POINTER_SIZE

    override fun malloc(count: Int, stack: MemoryStack): PointerBuffer = stack.mallocPointer(count)
    override fun calloc(count: Int, stack: MemoryStack): PointerBuffer = stack.callocPointer(count)
    override fun malloc(count: Int): PointerBuffer = MemoryUtil.memAllocPointer(count)
    override fun calloc(count: Int): PointerBuffer = MemoryUtil.memCallocPointer(count)
    override fun free(buffer: PointerBuffer) = MemoryUtil.memFree(buffer)
    override fun from(buffer: ByteBuffer): PointerBuffer = PointerBuffer.create(buffer)
}

data object PointerBufferBuilder {
    context(OnStack)
    @OptIn(ExperimentalContracts::class)
    inline fun calloc(size: Int, init: PointerBuffer.() -> Unit): PointerBuffer {
        contract { callsInPlace(init, InvocationKind.EXACTLY_ONCE) }
        return stack.callocPointer(size).apply(init)
    }

    context(OnStack)
    fun utf8(vararg strings: CharSequence): PointerBuffer {
        return calloc(strings.size) {
            repeat(strings.size) { i ->
                this[i] = strings[i]
            }
        }
    }

    context(OnStack)
    fun pointers(vararg pointers: Long): PointerBuffer {
        return stack.pointers(*pointers)
    }
}

context(OnStack)
operator fun PointerBuffer.set(index: Int, string: CharSequence): PointerBuffer = put(index, stack.UTF8(string))