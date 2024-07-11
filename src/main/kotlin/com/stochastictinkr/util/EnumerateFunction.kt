package com.stochastictinkr.util

import com.stochastictinkr.vulkan.*
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Defines a resource that can be enumerated into a buffer.
 *
 * @see use
 * @see forEach
 * @see map
 * @param B The buffer type.
 */
fun interface EnumerateFunction<B : Any> {
    /**
     * Enumerate the resource into a buffer, or get the count of the resource.
     * If [pValues] is null, the count is returned.
     * If [pValues] is not null, the resource is enumerated into it, up to the count in [pCount].
     *
     * @param pCount A buffer to store the count of the resource, or that has the size of the buffer.
     * @param pValues A buffer to store the resource, or null to get the count.
     */
    fun enumerate(pCount: IntBuffer, pValues: B?)
}

/**
 * Allocates a buffer on the stack.  The buffer is uninitialized.
 */
fun interface StackMalloc<B : Any> {
    fun malloc(count: Int, stack: MemoryStack): B
}

/**
 * Allocates a buffer on the stack.  The buffer is initialized to zero.
 */
fun interface StackCalloc<B : Any> {
    fun calloc(count: Int, stack: MemoryStack): B
}

/**
 * Allocates a buffer on the heap.  The buffer is uninitialized.
 */
fun interface HeapMalloc<B : Any> {
    fun malloc(count: Int): B
}

fun interface HeapFree<B : Any> {
    fun free(buffer: B)
}

/**
 * Allocates a buffer on the heap.  The buffer is initialized to zero.
 */
fun interface HeapCalloc<B : Any> {
    fun calloc(count: Int): B
}

/**
 * Wraps a buffer.
 */
fun interface ByteBufferWrapper<B : Any> {
    fun from(buffer: ByteBuffer): B
}

/**
 * A resource that has a size in bytes.
 */
interface ByteSized {
    val sizeOf: Int
}

/**
 * Use the enumerated resource in a block.  The resource is only valid until [use] returns.
 *
 * @see EnumerateFunction
 */
@OptIn(ExperimentalContracts::class)
inline fun <C, B : Any, R> C.use(block: (B) -> R): R where C : EnumerateFunction<B>, C : StackMalloc<B> {
    contract { callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE) }
    onStack {
        return block(allocOn(stack))
    }
}

/**
 * Performs the given [action] on each element of the resource.
 * The resource is only valid until [forEach] returns.
 *
 * @see EnumerateFunction
 */
@JvmName("forEachIterable")
@OptIn(ExperimentalContracts::class)
inline fun <C, E, B : Iterable<E>> C.forEach(action: (E) -> Unit) where C : EnumerateFunction<B>, C : StackMalloc<B> {
    contract { callsInPlace(action, kotlin.contracts.InvocationKind.UNKNOWN) }
    use { it.forEach(action) }
}

/**
 * Returns a list containing the results of applying the given [transform] function to each element in the resource.
 * The resource is only valid until [map] returns.
 *
 * @see EnumerateFunction
 */
@JvmName("mapIterable")
@OptIn(ExperimentalContracts::class)
inline fun <C, E1, B : Iterable<E1>, E> C.map(transform: (E1) -> E): List<E> where C : EnumerateFunction<B>, C : StackMalloc<B> {
    contract { callsInPlace(transform, kotlin.contracts.InvocationKind.UNKNOWN) }
    return use { it.map(transform) }
}

/**
 * Performs the given [action] on each element of the resource.
 * The resource is only valid until [forEach] returns.
 *
 * @see EnumerateFunction
 */
@JvmName("forEachIntBuffer")
@OptIn(ExperimentalContracts::class)
inline fun <C> C.forEach(action: (Int) -> Unit) where C : EnumerateFunction<IntBuffer>, C : StackMalloc<IntBuffer> {
    contract { callsInPlace(action, kotlin.contracts.InvocationKind.UNKNOWN) }
    use { it.forEach(action) }
}

/**
 * Returns a list containing the results of applying the given [transform] function to each element in the resource.
 * The resource is only valid until [map] returns.
 *
 * @see EnumerateFunction
 */
@JvmName("mapIntBuffer")
@OptIn(ExperimentalContracts::class)
inline fun <C, R> C.map(transform: (Int) -> R): List<R> where C : EnumerateFunction<IntBuffer>, C : StackMalloc<IntBuffer> {
    contract { callsInPlace(transform, kotlin.contracts.InvocationKind.UNKNOWN) }
    return use { it.map(transform) }
}

/**
 * Performs the given [action] on each element of the resource.
 * The resource is only valid until [forEach] returns.
 *
 * @see EnumerateFunction
 */
@JvmName("forEachLongBuffer")
@OptIn(ExperimentalContracts::class)
inline fun <C> C.forEach(action: (Long) -> Unit) where C : EnumerateFunction<LongBuffer>, C : StackMalloc<LongBuffer> {
    contract { callsInPlace(action, kotlin.contracts.InvocationKind.UNKNOWN) }
    use { it.forEach(action) }
}

/**
 * Returns a list containing the results of applying the given [transform] function to each element in the resource.
 * The resource is only valid until [map] returns.
 *
 * @see EnumerateFunction
 */
@JvmName("mapLongBuffer")
@OptIn(ExperimentalContracts::class)
inline fun <C, R> C.map(transform: (Long) -> R): List<R> where C : EnumerateFunction<LongBuffer>, C : StackMalloc<LongBuffer> {
    contract { callsInPlace(transform, kotlin.contracts.InvocationKind.UNKNOWN) }
    return use { it.map(transform) }
}

// PointerBuffer:
/**
 * Performs the given [action] on each element of the resource.
 * The resource is only valid until [forEach] returns.
 *
 * @see EnumerateFunction
 */
@JvmName("forEachPointerBuffer")
@OptIn(ExperimentalContracts::class)
inline fun <C> C.forEach(action: (Long) -> Unit) where C : EnumerateFunction<PointerBuffer>, C : StackMalloc<PointerBuffer> {
    contract { callsInPlace(action, kotlin.contracts.InvocationKind.UNKNOWN) }
    use { it.forEach(action) }
}

/**
 * Returns a list containing the results of applying the given [transform] function to each element in the resource.
 * The resource is only valid until [map] returns.
 *
 * @see EnumerateFunction
 */
@JvmName("mapPointerBuffer")
@OptIn(ExperimentalContracts::class)
inline fun <C, R> C.map(transform: (Long) -> R): List<R> where C : EnumerateFunction<PointerBuffer>, C : StackMalloc<PointerBuffer> {
    contract { callsInPlace(transform, kotlin.contracts.InvocationKind.UNKNOWN) }
    return use { it.map(transform) }
}

/**
 * Enumerate the resources into a buffer on the heap. The caller is responsible for freeing the resources.
 */
fun <C, B : Any> C.allocOn(@Suppress("UNUSED_PARAMETER") heap: Heap): B where C : EnumerateFunction<B>, C : HeapMalloc<B> =
    allocOn { count -> malloc(count) }

/**
 * Enumerate the resources into a buffer on the stack. The returned buffer is only valid as long as the
 * stack.
 */
infix fun <C, B : Any> C.allocOn(stack: MemoryStack): B where C : EnumerateFunction<B>, C : StackMalloc<B> =
    allocOn { count -> malloc(count, stack) }

inline infix fun <C, B : Any> C.allocOn(alloc: (Int) -> B): B where C : EnumerateFunction<B> {
    // since alloc might use the stack, we want to make sure we don't call it from within a stack block.
    val count = onStack {
        val pCount = stack.mallocInt(1)
        enumerate(pCount, null)
        pCount[0]
    }
    // allocate the buffer. If this uses the stack, it will be placed before the count buffer.
    val pValues = alloc(count)
    onStack { enumerate(stack.ints(count), pValues) }
    return pValues
}

/**
 * Enumerate the resources into the given buffer, returning the resource.
 * If the buffer is not large enough to hold the resources, the behavior dependent on the implementation.
 */
fun <C, B : Any> C.put(buffer: ByteBuffer): B where C : EnumerateFunction<B>, C : ByteBufferWrapper<B> {
    onStack {
        val pCount = stack.mallocInt(1)
        enumerate(pCount, null)
        val pValues = from(buffer)
        enumerate(pCount, pValues)
        return pValues
    }
}

/**
 * Determine the number of bytes required to store the resources.
 */
val <C> C.requiredBytes: Int where C : EnumerateFunction<*>, C : ByteSized
    get() = onStack {
        val pCount = stack.mallocInt(1)
        enumerate(pCount, null)
        pCount[0] * sizeOf
    }