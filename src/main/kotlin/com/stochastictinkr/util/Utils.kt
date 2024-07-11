@file:Suppress("unused")

package com.stochastictinkr.util

import org.lwjgl.PointerBuffer
import org.lwjgl.system.CustomBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.Pointer
import java.nio.IntBuffer
import java.nio.LongBuffer

fun PointerBuffer.asSequence() =
    0.until(limit()).asSequence().map { index -> get(index) }

inline fun <T> PointerBuffer.map(mapper: (Long) -> T): List<T> =
    0.until(limit()).map { index -> mapper(get(index)) }

inline fun PointerBuffer.forEach(action: (Long) -> Unit) {
    0.until(limit()).forEach { index -> action(get(index)) }
}

fun IntBuffer.asSequence() =
    0.until(limit()).asSequence().map { index -> get(index) }

inline fun <R> IntBuffer.map(mapper: (Int) -> R): List<R> {
    return 0.until(limit()).map { index -> mapper(get(index)) }
}

inline fun IntBuffer.forEach(action: (Int) -> Unit) {
    0.until(limit()).forEach { index -> action(get(index)) }
}

fun LongBuffer.asSequence() =
    0.until(limit()).asSequence().map { index -> get(index) }

inline fun <R> LongBuffer.map(mapper: (Long) -> R): List<R> {
    return 0.until(limit()).map { index -> mapper(get(index)) }
}

inline fun LongBuffer.forEach(action: (Long) -> Unit) {
    0.until(limit()).forEach { index -> action(get(index)) }
}

fun MemoryStack.utf8List(values: Collection<String>): PointerBuffer =
    callocPointer(values.size).also { pointers ->
        values.map(::UTF8).forEachIndexed(pointers::put)
    }

fun MemoryStack.utf8List(vararg values: String): PointerBuffer =
    callocPointer(values.size).also { pointers ->
        values.map(::UTF8).forEachIndexed(pointers::put)
    }

val Pointer?.address: Long get() = this?.address() ?: NULL

val CustomBuffer<*>.indices: IntRange get() = (0 until remaining())
