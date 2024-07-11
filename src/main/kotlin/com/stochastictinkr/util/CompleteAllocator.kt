package com.stochastictinkr.util

interface CompleteAllocator<B : Any> :
    StackMalloc<B>,
    HeapMalloc<B>,
    StackCalloc<B>,
    HeapCalloc<B>,
    HeapFree<B>,
    ByteBufferWrapper<B>,
    ByteSized
