package com.stochastictinkr.vulkan.playground.generator

import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.nio.file.Path

private val longType = java.lang.Long.TYPE

private const val heapParameter = "@Suppress(\"UNUSED_PARAMETER\") heap: Heap"

class HandleGenerator(private val root: Path, private val featureSet: FeatureSet) {
    private val reachableObjectsByType: Map<String?, List<ReachableObject>> by lazy {
        buildReachableObjectsByType()
    }

    fun generateHandleFile(type: Type?, details: HandleDetails?) {
        val handleClassName = handleClassName(type)
        val lwjglHandle = LwjglClasses.vulkan(handleClassName) ?: details?.alias?.let { LwjglClasses.vulkan(it) }
        val parentTypeName = handleParent(handleClassName, details)
        val parentField = parentTypeName?.removePrefix("Vk")?.replaceFirstChar(Char::lowercase)

        val lwjglParent = parentTypeName?.let { LwjglClasses.vulkan(it) }
        if (details?.alias != null) {
            createKotlinFile(root, OUTPUT_PACKAGE, handleClassName) {
                +"typealias $handleClassName = ${details.alias}"
            }
            return
        }

        createKotlinFile(root, OUTPUT_PACKAGE, handleClassName) {
            if (lwjglParent != null) import(lwjglParent)
            val closeMethod =
                featureSet
                    .commandMethods
                    .firstOrNull { isCloseMethod(it, type) }

            if (lwjglHandle == null) {
                if (type == null) {
                    "data object $handleClassName" {
                        +commandMethodsBuilders(null, null)
                    }
                } else {
                    val extends = if (closeMethod != null) ": AutoCloseable" else ""
                    val parentVal = parentField?.let { ", val $it: $parentTypeName" } ?: ""
                    "class $handleClassName(val handle: Long$parentVal)$extends" {
                        +"override fun toString() = \"$handleClassName(\$handle)\""
                        +commandMethodsBuilders(type, null)
                        +""
                        writeClose(receiver = null, closeMethod = closeMethod, handleType = type.name)
                    }
                }
            } else {
                type!!
                import(lwjglHandle)
                val receiver = lwjglHandle.fullName
                writeClose(receiver = receiver, closeMethod = closeMethod, handleType = type.name)
                +commandMethodsBuilders(type, receiver)
            }


        }
    }


    private val closeMethodRegex = Regex("^(?:vkDestroy|vkFree)")

    private fun isCloseMethod(commandMethod: CommandMethod, type: Type?): Boolean {
        val handleType = type?.name ?: return false
        closeMethodRegex.containsMatchIn(commandMethod.name) || return false

        val params = commandMethod.parameters.toMutableList()

        handleParent(handleType)?.let { parentType ->
            val parentParam = params.removeFirstOrNull() ?: return false
            parentParam.type == parentType || return false
        }

        val handleParam = params.removeFirstOrNull() ?: return false
        handleParam.type == handleType || return false

        val allocatorParam = params.singleOrNull() ?: return false
        return allocatorParam.type == "VkAllocationCallbacks"
    }

    private fun KotlinFileBuilder.writeClose(receiver: String?, closeMethod: CommandMethod?, handleType: String) {
        +defaultImplementation(
            receiver = receiver,
            handleType = handleType,
            commandMethod = closeMethod ?: return,
            reachableObjects = closeMethodReachables(handleType),
            methodName = "close",
            ignoreReturn = true,
            override = receiver == null,
        )
        if (receiver != null) {
            usesContracts()
            "inline fun <R> $receiver.use(block: $handleType.() -> R) : R" {
                +"contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }"
                `try` {
                    +"return block()"
                } finally {
                    +"close()"
                }
            }

            import("com.stochastictinkr.util.AutoCloseableWrapper")
            +"fun $receiver.asAutoCloseable() = AutoCloseableWrapper(this) { close() }"
        }

    }

    private fun closeMethodReachables(handleType: String) =
        reachableObjectsByType.getValue(handleType) + ReachableObject(
            false,
            "null",
            "VkAllocationCallbacks",
            LwjglClasses.vulkan("VkAllocationCallbacks")
        )

    private fun commandMethods(type: Type?) = featureSet
        .commandMethods
        .filter { it.parameters.none { param -> param.element.type.isArray } }
        .filter { belongsToType(it, type) }
        .distinctBy { methodNameFor(type?.name, it) + it.parameters.joinToString { param -> param.jvmType.name } }

    private fun belongsToType(it: CommandMethod, type: Type?): Boolean {
        if (type == null) {
            return !featureSet.isHandle(it.parameters.firstOrNull()?.type)
        }
        val firstParam = it.parameters.getOrNull(0) ?: return false
        if (firstParam.type == type.name) {
            return true
        }
        val secondParam = it.parameters.getOrNull(1) ?: return false
        return secondParam.type == type.name && handleParent(type.name) == firstParam.type
    }

    private fun commandMethodsBuilders(type: Type?, receiver: String?): Sequence<CommandMethodBuilder> {
        val handleType = type?.name
        val reachableObjects = reachableObjectsByType.getValue(handleType)
        return commandMethods(type)
            .flatMap { commandMethod ->
                validateCommandMethod(commandMethod)
                sequence {
                    yield(defaultImplementation(receiver, handleType, commandMethod, reachableObjects))
                    yieldAll(singleOutputCommandOverloads(receiver, handleType, commandMethod, reachableObjects))
//                    yieldAll(memoryMappingCommandOverloads(receiver, handleType, commandMethod, reachableObjects))
                    yieldAll(enumeratingCommandOverloads(receiver, handleType, commandMethod, reachableObjects))
                }
            }
    }


    private fun defaultImplementation(
        receiver: String?,
        handleType: String?,
        commandMethod: CommandMethod,
        reachableObjects: List<ReachableObject>,
        methodName: String = methodNameFor(handleType, commandMethod),
        ignoreReturn: Boolean = false,
        override: Boolean = false,
    ): CommandMethodBuilder {
        return CommandMethodBuilder(
            featureSet = featureSet,
            methodType = commandMethod.methodTypeString,
            methodName = methodName,
            receiver = receiver,
            override = override,
            reachableObjects = reachableObjects,
            reachableObjectsByType = reachableObjectsByType,
            ignoreReturn = ignoreReturn,
        ) {
            commandMethod.parameters.forEach { add(it) }
        }
    }

    private class SingleOutputBuilder(
        override val receiver: String?,
        override val handleType: String?,
        override val commandMethod: CommandMethod,
        override val reachableObjects: List<ReachableObject>,
        override val inputs: List<MatchedParameter>,
        override val output: MatchedParameter,
    ) : OutputFunctionBuilder

    private class EnumeratedOutputBuilder(
        val count: MatchedParameter,
        val builder: OutputFunctionBuilder,
    ) : OutputFunctionBuilder by builder {
        val countArgument = count.name
        val outputAllocator = when (val type = builder.output.jvmType) {
            PointerBuffer::class.java, IntBuffer::class.java, LongBuffer::class.java -> "${type.simpleName}Allocator"
            else -> "${type.outerMost.simpleName}Allocator"
        }
    }

    private fun singleOutputCommandOverloads(
        receiver: String?,
        handleType: String?,
        commandMethod: CommandMethod,
        reachableObjects: List<ReachableObject>,
    ): Sequence<CommandMethodBuilder> {
        val output = commandMethod.parameters.last().takeIf { !it.isConst && it.numPointers == 1 }
            ?: return emptySequence()

        if (commandMethod.parameters.count { !it.isConst && it.numPointers == 1 } != 1) {
            return emptySequence()
        }
        val inputs = commandMethod.parameters.subList(0, commandMethod.parameters.size - 1)
        val builder = SingleOutputBuilder(receiver, handleType, commandMethod, reachableObjects, inputs, output)

        return when (featureSet.categoryByTypeName[output.type]) {
            Category.HANDLE -> builder.singleHandleOutputCommand()
            Category.ENUM, Category.BITMASK -> builder.singleEnumOutputCommand()
            Category.BASETYPE -> when (output.type) {
                "VkBool32" -> builder.singleBooleanOutputCommand()
                "VkDeviceSize", "VkRemoteAddressNV" -> builder.singlePrimitiveOutputCommand("Long")
                else -> warn("Unsupported base type `${output.type}` for ${commandMethod.name}")
            }

            Category.STRUCT, Category.UNION -> builder.singleStructOutputCommandOverloads()
            else -> when (output.type) {
                "void", "Display" -> return emptySequence()
                else -> {
                    val primitiveType = when (output.jvmType) {
                        IntBuffer::class.java -> "Int"
                        LongBuffer::class.java -> "Long"
                        else -> return warn("Unsupported output type `${output.jvmType}` for ${commandMethod.name}")
                    }
                    builder.singlePrimitiveOutputCommand(primitiveType)
                }

            }

        }
    }

    private fun warn(warning: String): Sequence<CommandMethodBuilder> {
        val at =
            Thread
                .currentThread()
                .stackTrace
                .getOrNull(2)
                ?.let { "\n\tat ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
                ?: ""

        System.err.println("WARNING: $warning$at")
        return emptySequence()
    }

    private fun OutputFunctionBuilder.singleHandleOutputCommand(): Sequence<CommandMethodBuilder> {
        if (output.jvmType !in setOf(PointerBuffer::class.java, LongBuffer::class.java)) {
            return warn("Invalid buffer type for handle output")
        }
        val lwjglHandle = LwjglClasses.vulkan(handleClassName(output.type))
        val constructor = lwjglHandle?.run {
            constructors
                .firstOrNull { isSupportedHandleConstructor(it, commandMethod, reachableObjects) }
                ?: return warn("Failed to find a suitable constructor for ${output.type} in ${commandMethod.name}")
        }
        return sequence {
            commandMethodBuilder {
                LwjglClasses.vulkan(output.type)?.let { import(it) }
                needsStack = true
                val isList = output.len.isNotEmpty()
                val constructorBuilder = constructHandle(constructor, isList, output.name)
                if (isList) {
                    this.import("com.stochastictinkr.util.map")
                    returnStatement =
                        "return ${output.name}.map { ${constructorBuilder.name}(${
                            constructorBuilder.arguments.joinToString(", ")
                        }) }"
                    returnType = "List<${constructorBuilder.name}>"
                }
            }
        }
    }

    context(OutputFunctionBuilder)
    private fun CommandMethodBuilder.constructHandle(
        handleConstructor: Constructor<*>?,
        isList: Boolean,
        outputParam: String,
    ) = thenConstruct(handleClassName(output.type)) {
        handleConstructor?.declaringClass?.let { import(it) }
        if (isList) {
            addArgument("it")
        } else {
            addArgument("$outputParam[0]")
        }
        // Special cases
        when (methodType.name) {
            "vkAllocateDescriptorSets" ->
                addReachable(
                    ReachableObject(
                        isSelf = false,
                        path = "VkDescriptorPool(pAllocateInfo.descriptorPool(), this@VkDevice)",
                        typeName = "VkDescriptorPool",
                        jvmType = null,
                    )
                )

            "vkCreateVideoSessionParametersKHR" ->
                addReachable(
                    ReachableObject(
                        isSelf = false,
                        path = "VkVideoSessionKHR(pCreateInfo.videoSession(), this@VkDevice)",
                        typeName = "VkVideoSessionKHR",
                        jvmType = null,
                    )
                )
        }
        if (handleConstructor != null) {
            handleConstructor.parameters
                .drop(1)
                .forEach { param -> add(param.toTypeString()) }
        } else {
            val parent = handleParent(output.type)
            if (parent != null) {
                add(LwjglClasses.vulkan(handleClassName(parent)), handleClassName(parent))
            }
        }
    }


    private fun OutputFunctionBuilder.singleEnumOutputCommand(): Sequence<CommandMethodBuilder> {
        if (output.len.isNotEmpty()) {
            return warn("Output param ${output.name} has a length ${output.len} in ${commandMethod.name}")
        }
        val enumType = featureSet.enumType(output.type) ?: return warn("Failed to find enum type for ${output.type}")

        when (enumType.valueType) {
            "Int" -> if (output.jvmType != IntBuffer::class.java) {
                return warn("Expected IntBuffer for enum output, but got ${output.jvmType} in ${commandMethod.name}")
            }

            "Long" -> if (output.jvmType != LongBuffer::class.java) {
                return warn("Expected LongBuffer for enum output, but got ${output.jvmType} in ${commandMethod.name}")
            }

            else -> return warn("Unsupported enum value type `${enumType.valueType}` for ${commandMethod.name}")
        }

        return sequence {
            commandMethodBuilder {
                needsStack = true
                returnType = output.type
                returnStatement = "return ${output.type}(${output.name}[0])"
            }
        }
    }

    private fun OutputFunctionBuilder.singleBooleanOutputCommand() =
        singlePrimitiveOutputCommand("Boolean") { returnStatement = "return ${output.name}[0] != 0" }

    private fun OutputFunctionBuilder.singlePrimitiveOutputCommand(
        type: String,
        configure: CommandMethodBuilder.() -> Unit = {},
    ): Sequence<CommandMethodBuilder> {
        if (output.len.isNotEmpty()) {
            return warn("Output param ${output.name} has a length ${output.len} in ${commandMethod.name}")
        }
        return sequence {
            commandMethodBuilder {
                needsStack = true
                returnType = type
                returnStatement = "return ${output.name}[0]"
                configure()
            }
        }
    }

    private fun OutputFunctionBuilder.singleStructOutputCommandOverloads(): Sequence<CommandMethodBuilder> {
        val isBuffer = output.jvmType.simpleName == "Buffer"

        val length =
            when {
                isBuffer && output.len.isEmpty() -> "1"
                isBuffer -> lengthExpression(output.len, commandMethod)
                output.len.singleOrNull() == "1" -> ""
                output.len.isNotEmpty() -> return warn("Output param is not a buffer, but has a length ${output.len} in ${commandMethod.name}")
                else -> ""
            }

        val lengthArg = if (length.isEmpty()) "" else "$length, "

        return sequence {
            structMethodBuilder(
                lastParam = "stack: MemoryStack",
                allocator = "${output.type}.malloc(${lengthArg}stack)"
            ) { import(MemoryStack::class.java) }

            structMethodBuilder(
                lastParam = heapParameter,
                allocator = "${output.type}.malloc(${lengthArg})"
            )

            val lambdaParam = if (length.isEmpty()) "" else "Int"
            structMethodBuilder(
                lastParam = "alloc: ($lambdaParam)->${output.jvmType.fullName}",
                allocator = "alloc($length)"
            ) {
                inline = true
                lambdaArguments.add("alloc")
            }
        }
    }

    context(OutputFunctionBuilder)
    private suspend fun SequenceScope<CommandMethodBuilder>.structMethodBuilder(
        lastParam: String,
        allocator: String,
        configure: CommandMethodBuilder.() -> Unit = {},
    ) {
        yield(
            CommandMethodBuilder(
                featureSet,
                commandMethod.methodTypeString,
                methodNameFor(handleType, commandMethod),
                receiver, false, reachableObjects, reachableObjectsByType
            ) {
                import(output.jvmType)
                inputs.forEach { add(it) }
                createVals.add("val ${output.name} = $allocator")
                parameters.add(lastParam)
                arguments.add(output.name)
                returnType = output.type
                returnStatement = "return ${output.name}"
                configure()
            }
        )
    }

    context(EnumeratedOutputBuilder)
    private suspend fun SequenceScope<CommandMethodBuilder>.enumerableMethodBuilder(
        lastParam: String,
        allocator: String,
        configure: CommandMethodBuilder.() -> Unit = {},
    ) {
        yield(
            CommandMethodBuilder(
                featureSet,
                commandMethod.methodTypeString,
                methodNameFor(handleType, commandMethod),
                receiver,
                false,
                reachableObjects,
                reachableObjectsByType,
                inline = false,
                skipContracts = true,
                evalWrapper = {
                    "return CompleteEnumerable($outputAllocator)"(lambdaParams = "${count.name}, ${output.name}") {
                        it()
                    }
                    import("com.stochastictinkr.util.allocOn")
                    +".allocOn($allocator)"
                }
            ) {
                import(output.jvmType)
                inputs.forEach { add(it) }
                parameters.add(lastParam)
                arguments.add(count.name)
                arguments.add(output.name)
                returnType = output.jvmType.fullName
                configure()
            }
        )

    }


    private fun enumeratingCommandOverloads(
        receiver: String?,
        handleType: String?,
        commandMethod: CommandMethod,
        reachableObjects: List<ReachableObject>,
    ): Sequence<CommandMethodBuilder> {
        if (commandMethod.parameters.count { it.numPointers == 1 && !it.isConst } != 2) return emptySequence()
        val (count, output) = commandMethod.parameters.takeLast(2)
        if (output.len != listOf(count.name)) return emptySequence()
        if (count.optional != listOf(false, true)) return emptySequence()
        if (count.type == "size_t") return emptySequence()
        if (count.type != "uint32_t") return warn("Count parameter `${count.name}` is not a uint32_t for ${commandMethod.name}")

        val inputs = commandMethod.parameters.subList(0, commandMethod.parameters.size - 2)

        EnumeratedOutputBuilder(
            count,
            SingleOutputBuilder(receiver, handleType, commandMethod, reachableObjects, inputs, output)
        ).run {
            return sequence {
                val numMethod =
                    "num" + methodNameFor(handleType, commandMethod)
                        .removePrefix("enumerate")
                        .removePrefix("get")

                with(SingleOutputBuilder(receiver, handleType, commandMethod, reachableObjects, inputs, count)) {
                    yieldAll(
                        singlePrimitiveOutputCommand("Int") {
                            methodName = numMethod
                            returnStatement = "return ${count.name}[0]"
                            arguments.add("null")
                        }
                    )
                }

                //1. The last parameter is a `MemoryStack` and the result is allocated on the stack. Available for all types.
                enumerableMethodBuilder(
                    lastParam = "stack: MemoryStack",
                    allocator = "stack",
                ) { import(MemoryStack::class.java) }
                //2. The last parameter is the singleton `Heap` object and the result is allocated on the heap. Available for all types.
                enumerableMethodBuilder(
                    lastParam = heapParameter,
                    allocator = "heap",
                )
                //3. The last parameter is a `(Int)->Buffer` lambda that creates the result buffer. Available for all types.
                enumerableMethodBuilder(
                    lastParam = "alloc: (Int)->${output.jvmType.fullName}",
                    allocator = "alloc",
                ) {
                    import(output.jvmType)
                    lambdaArguments.add("alloc")
                }
                //4. The last parameter is a pre-allocated buffer. This overload will return a VkResult if the underlying LWJGL method
                //   returns a VkResult. Available for all types.
                // This is the default implementation, so no need to yield it.
                //5. A command that returns an `CompleteEnumerable` object, which can be used to iterate over the results without needing
                //   to handle the allocation of the buffer explicitly. Available for Structs and Unions.
                when (val category = featureSet.categoryByTypeName.getValue(output.type)) {
                    Category.STRUCT, Category.UNION -> structEnumerator()
                    //6. A command that returns a List of the results. Available for Enums, Bitmasks, and Handles.
                    Category.BITMASK, Category.ENUM -> wrappedValueList(
                        map = { +".map { ${output.type}(it) }" },
                        configure = {}
                    )

                    Category.HANDLE -> {
                        val lwjglHandle = LwjglClasses.vulkan(output.type)
                        val constructor = lwjglHandle?.run {
                            constructors.firstOrNull {
                                isSupportedHandleConstructor(it, commandMethod, reachableObjects)
                            }
                        }

                        if (lwjglHandle != null && constructor == null) {
                            warn("Failed to find a suitable constructor for ${output.type} in ${commandMethod.name}")
                        } else
                            wrappedValueList(
                                configure = {
                                    import("com.stochastictinkr.util.map")
                                    lwjglHandle?.let { import(it) }
                                    constructHandle(constructor, true, output.name)
                                },
                                map = { constructorBuilder ->
                                    +".map { ${constructorBuilder.name}(${constructorBuilder.arguments.joinToString(", ")}) }"
                                }
                            )
                    }

                    else -> warn("Unsupported category `${category}` of `${output.type}` for ${commandMethod.name}")
                }
            }
        }
    }

    context(EnumeratedOutputBuilder)
    private suspend fun SequenceScope<CommandMethodBuilder>.structEnumerator() = yield(
        CommandMethodBuilder(
            featureSet,
            commandMethod.methodTypeString,
            methodNameFor(handleType, commandMethod),
            receiver,
            false,
            reachableObjects,
            reachableObjectsByType,
            returnType = "CompleteEnumerable<${output.jvmType.fullName}>",
            inline = false,
            evalWrapper = {
                "return CompleteEnumerable($outputAllocator)"(lambdaParams = "${count.name}, ${output.name}") {
                    it()
                }
            },
        ) {
            import("com.stochastictinkr.util.CompleteEnumerable")
            import(output.jvmType)
            inputs.forEach { add(it) }
            arguments.add(count.name)
            arguments.add(output.name)
        }
    )

    context(EnumeratedOutputBuilder)
    private suspend fun <R> SequenceScope<CommandMethodBuilder>.wrappedValueList(
        map: KotlinFileBuilder.(R) -> Unit,
        configure: CommandMethodBuilder.() -> R,
    ) {
        yield(
            CommandMethodBuilder(
                featureSet,
                commandMethod.methodTypeString,
                methodNameFor(handleType, commandMethod),
                receiver,
                false,
                reachableObjects,
                reachableObjectsByType,
                returnType = "List<${output.type}>",
                returnStatement = "",
                inline = false,
            ) {
                import(output.jvmType)
                inputs.forEach { add(it) }
                arguments.add(count.name)
                arguments.add(output.name)
                val constructor = configure()
                evalWrapper = {
                    "return CompleteEnumerable($outputAllocator)"(lambdaParams = "${count.name}, ${output.name}") {
                        it()
                    }
                    map(constructor)
                }
            }
        )
    }

    interface OutputFunctionBuilder {
        val receiver: String?
        val handleType: String?
        val commandMethod: CommandMethod
        val reachableObjects: List<ReachableObject>
        val inputs: List<MatchedParameter>
        val output: MatchedParameter
    }

    context(OutputFunctionBuilder)
    private suspend fun SequenceScope<CommandMethodBuilder>.commandMethodBuilder(configure: CommandMethodBuilder.() -> Unit) {
        yield(
            CommandMethodBuilder(
                featureSet,
                commandMethod.methodTypeString,
                methodNameFor(handleType, commandMethod),
                receiver, false, reachableObjects, reachableObjectsByType,
            ) {
                inputs.forEach { add(it) }
                val length = if (output.len.isEmpty()) "1" else {
                    lengthExpression(output.len, commandMethod)
                }
                val mallocType = output.jvmType.simpleName.removeSuffix("Buffer")
                createVals.add("val ${output.name} = stack.malloc$mallocType($length)")
                arguments.add(output.name)
                configure()
            }
        )
    }

    /*
        private fun enumerableFunctionBuilder(
            receiver: String?,
            handleType: String?,
            commandMethod: CommandMethod,
            methodName: String = methodNameFor(handleType, commandMethod),
            reachableObjects: List<ReachableObject> = reachableObjectsByType.getValue(handleType),
            override: Boolean,
        ): CommandMethodBuilder? {
            val (_, method) = commandMethod
            val methodType = method.toTypeString()
            val parameters = methodType.parameters
            val matchedParameters = commandMethod.matchedParameters
            if (matchedParameters.count { it.numPointers == 1 && !it.isConst } != 2) return null
            val (countParam, valueParam) = matchedParameters.takeLast(2)
            if (valueParam.declaredParam.len != listOf(countParam.name)) return null
            if (countParam.optional != listOf(false, true)) return null
            if (countParam.type != "uint32_t") return null
            if (valueParam.optional != listOf(true)) return null

            validateCommandMethod(methodType, parameters, method)
            val inputs = matchedParameters.dropLast(2)

            // Find the allocator for the valueParam:
            val category = featureSet.categoryByTypeName.getValue(valueParam.type)
            val completeAllocatorClassName: String =
                if (category == Category.STRUCT) valueParam.type + "Allocator"
                else "${valueParam.jvmType.fullName}Allocator"

            return CommandMethodBuilder(
                featureSet = featureSet,
                methodType = methodType,
                methodName = methodName,
                receiver = receiver,
                override = override,
                reachableObjects = reachableObjects,
                reachableObjectsByType = reachableObjectsByType,
                methodComment = "Enumerable method",
                returnType = "CompleteEnumerable<${valueParam.jvmType.fullName}>",
                inline = false,
                evalWrapper = {
                    "return CompleteEnumerable($completeAllocatorClassName)"(lambdaParams = "pCount, pValues") {
                        it()
                    }
                },
                skipContracts = true,
            ) {
                import("com.stochastictinkr.util.CompleteEnumerable")
                import(valueParam.jvmType)
                inputs.forEach { add(it) }
                arguments.add("pCount")
                arguments.add("pValues")
            }
        }
    */

    /*
        private fun getFunctionBuilder(
            receiver: String?,
            handleType: String?,
            commandMethod: CommandMethod,
            methodName: String = methodNameFor(handleType, commandMethod),
            reachableObjects: List<ReachableObject> = reachableObjectsByType.getValue(handleType),
            override: Boolean,
        ): CommandMethodBuilder? {
            val (_, method) = commandMethod
            val methodType = method.toTypeString()
            val parameters = methodType.parameters
            validateCommandMethod(methodType, parameters, method)
            val matchedParameters = commandMethod.matchedParameters
            if (commandMethod.command.params.count { it.numPointers == 1 && !it.isConst } != 1) return null
            val inputs = matchedParameters.dropLast(1)
            val output = matchedParameters.lastOrNull() ?: return null
            if (output.isConst) return null
            val outputType = output.type
            val outputCategory = featureSet.categoryByTypeName[outputType]
            if (outputCategory !in setOf(Category.HANDLE, Category.ENUM, Category.BITMASK, Category.BASETYPE)) return null

            if (output.declaredParam.len.isNotEmpty()) {
                if (outputCategory != Category.HANDLE) {
                    System.err.println("WARNING: Output param ${output.name} has a length ${output.declaredParam.len} in ${commandMethod.command.name}")
                    return null
                }
            }

            if (Category.BASETYPE == outputCategory && outputType !in setOf(
                    "VkBool32",
                    "VkDeviceSize",
                    "VkRemoteAddressNV"
                )
            ) {
                System.err.println("WARNING: Unsupported return type $outputType in ${commandMethod.command.name}")
                return null
            }
            if (parameters.last().jvmType !in setOf(
                    PointerBuffer::class.java,
                    LongBuffer::class.java,
                    IntBuffer::class.java
                )
            ) return null

            if (outputCategory == Category.HANDLE) {
                LwjglClasses.vulkan(handleClassName(outputType))?.constructors?.any { constructor ->
                    isSupportedHandleConstructor(constructor, commandMethod, reachableObjects)
                }?.let {
                    if (!it) {
                        System.err.println("WARNING: Failed to find a suitable constructor for $outputType in ${commandMethod.command.name}")
                        return null
                    }
                }
                val parent = handleParent(outputType)
                if (reachableObjects.none { it.typeName == parent }) return null
            }

            return CommandMethodBuilder(
                featureSet = featureSet,
                methodType = methodType,
                methodName = methodName,
                receiver = receiver,
                override = override,
                reachableObjects = reachableObjects,
                reachableObjectsByType = reachableObjectsByType,
                methodComment = "Get method",
                needsStack = true
            ) {
                LwjglClasses.vulkan(output.type)?.let { import(it) }
                inputs.forEach { add(it) }
                val mallocType = output.jvmType.simpleName.removeSuffix("Buffer")
                val length = if (output.declaredParam.len.isEmpty()) "1" else {
                    lengthExpression(output.declaredParam.len, commandMethod)
                }
                createVals.add("val ${output.name} = stack.malloc$mallocType($length)")
                arguments.add(output.name)
                when {
                    outputCategory == Category.HANDLE -> {
                        thenConstruct(handleClassName(output.type)) {
                            if (output.declaredParam.len.isNotEmpty()) {
                                addArgument("it")
                            } else {
                                addArgument(output.name + "[0]")
                            }
                            val constructor = LwjglClasses
                                .vulkan(handleClassName(output.type))
                                ?.constructors
                                ?.first { isSupportedHandleConstructor(it, commandMethod, reachableObjects) }
                            if (constructor != null) {
                                constructor.parameters
                                    .drop(1)
                                    .forEach { param -> add(param.toTypeString()) }
                            } else {
                                val parent = handleParent(output.type)
                                if (parent != null) {
                                    add(LwjglClasses.vulkan(handleClassName(parent)), handleClassName(parent))
                                }
                            }
                        }
                        if (output.declaredParam.len.isNotEmpty()) {
                            val mapBody =
                                returnValueConstructor?.let { constructor ->
                                    "${constructor.name}(${constructor.arguments.joinToString(", ")})"
                                }
                            import("com.stochastictinkr.util.map")
                            returnStatement = "return ${output.name}.map { $mapBody }"
                            returnType = "List<${returnValueConstructor?.name}>"
                        }
                    }

                    output.type == "VkBool32" -> {
                        returnType = "Boolean"
                        returnStatement = "return ${output.name}[0] != 0"
                    }

                    output.type in setOf("VkDeviceSize", "VkRemoteAddressNV") -> {
                        returnType = "Long"
                        returnStatement = "return ${output.name}[0]"
                    }

                    outputCategory == Category.ENUM || outputCategory == Category.BITMASK -> {
                        returnType = output.type
                        returnStatement = "return ${output.type}(${output.name}[0])"
                    }

                    else -> {
                        System.err.println("WARNING: Unsupported return type: ${commandMethod.command.name}")
                    }
                }
            }
        }
    */

    private fun lengthExpression(
        len: List<String>,
        commandMethod: CommandMethod,
    ): String {
        val lenParam = len.single()
        if (lenParam in commandMethod.autoSizedParams) {
            return commandMethod.parameters.first { it.len.contains(lenParam) }.name + ".remaining()"
        }
        // If the length parameter is a removed AutoSize, then the length is the input AutoSized parameter's `.remaining()`
        val dependentParam = commandMethod.declaration.params.firstOrNull { it.name == lenParam }
        if (dependentParam != null) {
            return dependentParam.name
        }
        // If it matches "parameter->size", then the length is a member of the struct.
        val pattern = Regex("""(\w+)->(\w+)""")
        val match = pattern.matchEntire(lenParam)
        if (match != null) {
            val (structParam, sizeParam) = match.destructured
            if (commandMethod.parameters.any { it.name == structParam }) {
                return "$structParam.$sizeParam()"
            }
        }

        error("Unknown length parameter $lenParam for ${commandMethod.name}")
    }

    private fun validateCommandMethod(commandMethod: CommandMethod) {
        val returnType = commandMethod.methodTypeString
        check((returnType.jvmType == Void.TYPE || returnType.nativeType != null)) {
            "Missing NativeType annotation for method return ${commandMethod.name}"
        }
        commandMethod.parameters.forEach {
//            check(it.nativeType != null) {
//                "Missing NativeType annotation for parameter ${it.name} in ${commandMethod.name}"
//            }
            check(featureSet.typeExists(it.type)) {
                "Unknown parameter type ${it.type} in ${commandMethod.name}"
            }
        }
    }

    private fun methodNameFor(handleType: String?, commandMethod: CommandMethod): String {
        var commandName = commandMethod.name
        commandName = commandName.removePrefix("vk")

        if (handleType != null) {
            val prefix = handleType.removePrefix("Vk")
            commandName = commandName
                .replace(Regex("^(Get)?$prefix"), "\$1")
                .removeSuffix(prefix)
        }
        return commandName.replaceFirstChar(Char::lowercase)
    }

    private fun buildReachableObjectsByType(): MutableMap<String?, List<ReachableObject>> {
        val reachableObjectsByType = mutableMapOf<String?, List<ReachableObject>>()

        fun calculateReachableObjects(type: String): List<ReachableObject> {
            val className = handleClassName(type)
            val lwjglType = LwjglClasses.vulkan(className)
            val handlePath = if (lwjglType == null) "handle" else "address()"
            val parentTypeName = handleParent(type)
            val getterName = "get${parentTypeName?.removePrefix("Vk")}"
            val parentGetter: Method? = lwjglType?.methods?.find {
                it.name == getterName && it.parameters.isEmpty()
            }
            val parentField = if (lwjglType == null) {
                parentTypeName?.removePrefix("Vk")
            } else {
                parentGetter?.name?.removePrefix("get")
            }?.replaceFirstChar(Char::lowercase)

            return buildList {
                add(ReachableObject(true, "this@$className", type, lwjglType))
                add(ReachableObject(true, handlePath, type, longType))
                if (parentField != null && parentTypeName != null) {
                    addAll(
                        reachableObjectsByType.getOrPut(parentTypeName) { calculateReachableObjects(parentTypeName) }
                            .map { parentProperty ->
                                if (parentProperty.path.startsWith("this@")) parentProperty.copy(
                                    isSelf = false,
                                    path = parentField
                                )
                                else parentProperty.copy(
                                    isSelf = false,
                                    path = "$parentField.${parentProperty.path}"
                                )
                            }
                    )
                }
            }
        }
        featureSet.handleTypes.forEach { type ->
            reachableObjectsByType.getOrPut(type.name) { calculateReachableObjects(type.name) }
        }

        return reachableObjectsByType.withDefault { emptyList() }
    }

    companion object {
        fun handleClassName(handleType: String?) = when (handleType) {
            "VkDebugReportCallbackEXT" -> "VkDebugReportCallbackEXTHandle"
            null -> "Vulkan"
            else -> handleType
        }

        fun handleClassName(handle: Type?) = handleClassName(handle?.name)

        private fun isSupportedHandleConstructor(
            constructor: Constructor<*>,
            commandMethod: CommandMethod,
            reachableObjects: List<ReachableObject>,
        ): Boolean {
            val params = constructor.parameters.map { it.toTypeString() }.toMutableList()
            val addressParam = params.removeFirstOrNull() ?: return false
            // first must be a long,
            addressParam.jvmType == longType || return false

            // the second parameter may be a parent.
            val secondParam = params.removeFirstOrNull() ?: return false
            val finalParam =
                if (reachableObjects.any { it.typeName == secondParam.type }) params.removeFirstOrNull()
                else secondParam

            // There never be more than one parameter after the parent
            params.isEmpty() || return false

            // The parameter after the handle and parent (if it exists) must be available from the params.
            return finalParam == null || commandMethod.parameters.any { it.type == finalParam.type }
        }
    }

    private fun handleParent(handleType: String) =
        when (handleType) {
            "VkCommandBuffer" -> "VkDevice" // LWJGL uses VkDevice for VkCommandBuffer, not VkCommandPool.
            else -> featureSet.handleDetails(handleType).parent
        }


    private fun handleParent(
        handleType: String, details: HandleDetails?,
    ) =
        when (handleType) {
            "VkCommandBuffer" -> "VkDevice" // LWJGL uses VkDevice for VkCommandBuffer, not VkCommandPool.
            else -> details?.parent
        }
}

