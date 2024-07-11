package com.stochastictinkr.util

/**
 * Composes the [EnumerateFunction], [StackMalloc], [HeapMalloc], [ByteBufferWrapper], and [ByteSized] interfaces.
 */
interface CompleteEnumerable<B : Any> : EnumerateFunction<B>, CompleteAllocator<B>

fun <B : Any> CompleteEnumerable(
    stackMalloc: StackMalloc<B>,
    stackCalloc: StackCalloc<B>,
    heapMalloc: HeapMalloc<B>,
    heapCalloc: HeapCalloc<B>,
    heapFree: HeapFree<B>,
    byteBufferWrapper: ByteBufferWrapper<B>,
    sizeOf: Int,
    enumerateFunction: EnumerateFunction<B>,
) = CompleteEnumerable(
    CompleteAllocator(
        stackMalloc, stackCalloc, heapMalloc, heapCalloc, heapFree, byteBufferWrapper, sizeOf
    ),
    enumerateFunction
)

fun <B : Any> CompleteEnumerable(
    allocator: CompleteAllocator<B>,
    enumerateFunction: EnumerateFunction<B>,
): CompleteEnumerable<B> = object : CompleteEnumerable<B>,
    EnumerateFunction<B> by enumerateFunction,
    CompleteAllocator<B> by allocator {
}

fun <B : Any> CompleteAllocator(
    stackMalloc: StackMalloc<B>,
    stackCalloc: StackCalloc<B>,
    heapMalloc: HeapMalloc<B>,
    heapCalloc: HeapCalloc<B>,
    heapFree: HeapFree<B>,
    byteBufferWrapper: ByteBufferWrapper<B>,
    sizeOf: Int,
): CompleteAllocator<B> = object : CompleteAllocator<B>,
    StackMalloc<B> by stackMalloc,
    StackCalloc<B> by stackCalloc,
    HeapMalloc<B> by heapMalloc,
    HeapCalloc<B> by heapCalloc,
    HeapFree<B> by heapFree,
    ByteBufferWrapper<B> by byteBufferWrapper,
    ByteSized {
    override val sizeOf: Int = sizeOf
}

