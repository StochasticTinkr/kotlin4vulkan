package com.stochastictinkr.util

class AutoCloseableWrapper<T>(val value: T, private val close: (T) -> Unit) : AutoCloseable {
    override fun close() {
        close(value)
    }

    operator fun component1() = value
}
