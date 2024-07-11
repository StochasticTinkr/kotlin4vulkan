package com.stochastictinkr.util

import org.lwjgl.system.MemoryStack
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

interface OnStack {
    val stack: MemoryStack
}

class OnStackImpl(override val stack: MemoryStack) : OnStack

@OptIn(ExperimentalContracts::class)
inline fun <R> onStack(block: OnStack.() -> R): R {
    contract { callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE) }
    return MemoryStack.stackPush().use {
        OnStackImpl(it).block()
    }
}
