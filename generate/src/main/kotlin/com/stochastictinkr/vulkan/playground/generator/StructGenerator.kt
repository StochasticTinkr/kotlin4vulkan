package com.stochastictinkr.vulkan.playground.generator

import org.lwjgl.system.MemoryStack
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.file.Path

class StructGenerator(private val root: Path, val featureSet: FeatureSet) {
    private val memoryStackClass = LwjglClasses.system("MemoryStack") ?: error("No MemoryStack class found")

    fun generateStructFile(structType: Type, details: StructDetails) {
        val structName = structType.name
        val structClass = LwjglClasses.vulkan(structName)
        val bufferName = "$structName.Buffer"
        val bufferClass = LwjglClasses.vulkan(bufferName)

        if (structClass == null || bufferClass == null) {
            structType.alias?.let { writeStructAlias(it, structType.name) }
            return
        }

        val mallocStack =
            structClass.method("malloc", memoryStackClass, returns = structClass, isStatic = true)
        val callocStack =
            structClass.method("calloc", memoryStackClass, returns = structClass, isStatic = true)
        val mallocHeap = structClass.method("malloc", returns = structClass, isStatic = true)
        val callocHeap = structClass.method("calloc", returns = structClass, isStatic = true)

        val mallocBufferStack =
            structClass.method("malloc", Int::class.java, memoryStackClass, returns = bufferClass, isStatic = true)
        val callocBufferStack =
            structClass.method("calloc", Int::class.java, memoryStackClass, returns = bufferClass, isStatic = true)
        val mallocBufferHeap = structClass.method("malloc", Int::class.java, returns = bufferClass, isStatic = true)
        val callocBufferHeap = structClass.method("calloc", Int::class.java, returns = bufferClass, isStatic = true)

        val allAllocators = listOfNotNull(mallocBufferStack, callocBufferStack, mallocBufferHeap, callocBufferHeap)
        val bufferComplete = allAllocators.isNotEmpty()

        createKotlinFile(root, OUTPUT_PACKAGE, structName) {
            import(structClass)
            if (bufferComplete) {
                import("com.stochastictinkr.util.CompleteAllocator")
                import(ByteBuffer::class)
                import(memoryStackClass)
                "data object ${structName}Allocator : CompleteAllocator<$bufferName>" {
                    +"override val sizeOf: Int = $structName.SIZEOF"
                    listOf("malloc", "calloc").forEach { name ->
                        +"override fun $name(count: Int, stack: MemoryStack): $bufferName = ${structName}.$name(count, stack)"
                        +"override fun $name(count: Int): $bufferName = ${structName}.$name(count)"
                    }
                    +"override fun from(buffer: ByteBuffer): $bufferName = ${bufferName}(buffer)"
                    +"override fun free(buffer: $bufferName) = buffer.free()"
                }
                +""
            }

            val memberNames = details.members.map { it.name }.toSet() - setOf("pNext", "sType")

            import(MemoryStack::class)

            // Single on stack
            writeConstructor(
                structName,
                structClass,
                defaultAllocMethod = callocStack ?: mallocStack,
                allocParams = listOf("stack: MemoryStack"),
                allocLambdaParam = "MemoryStack",
                allocLambdaArgument = "stack",
                builderType = "${structName}Builder",
                resultType = structName
            )

            // Single on heap
            writeConstructor(
                structName,
                structClass,
                defaultAllocMethod = callocHeap ?: mallocHeap,
                allocParams = listOf("@Suppress(\"UNUSED_PARAMETER\") heap: Heap"),
                allocLambdaParam = "",
                allocLambdaArgument = "",
                builderType = "${structName}Builder",
                resultType = structName
            )

            // Multiple on stack
            writeConstructor(
                structName,
                structClass,
                defaultAllocMethod = callocBufferStack ?: mallocBufferStack,
                allocParams = listOf("count: Int", "stack: MemoryStack"),
                allocLambdaParam = "Int, MemoryStack",
                allocLambdaArgument = "count, stack",
                builderType = "${structName}BufferBuilder",
                resultType = bufferName
            )

            // Multiple on heap
            writeConstructor(
                structName,
                structClass,
                defaultAllocMethod = callocBufferHeap ?: mallocBufferHeap,
                allocParams = listOf("@Suppress(\"UNUSED_PARAMETER\") heap: Heap", "count: Int"),
                allocLambdaParam = "Int",
                allocLambdaArgument = "count",
                builderType = "${structName}BufferBuilder",
                resultType = bufferName
            )

            "data object ${structName}Builder" {
                processMembers(structClass, memberNames)
            }
            +""
            "data object ${structName}BufferBuilder" {
                processMembers(bufferClass, memberNames)
            }
            +""


        }
    }

    context(KotlinFileBuilder)
    private fun writeConstructor(
        structName: String,
        structClass: Class<*>,
        defaultAllocMethod: Method?,
        allocParams: List<String>,
        allocLambdaParam: String,
        allocLambdaArgument: String,
        builderType: String,
        resultType: String,
    ) {
        if (defaultAllocMethod == null) return
        import(defaultAllocMethod.declaringClass)
        val defaultAllocator = "${defaultAllocMethod.declaringClass.fullName}::${defaultAllocMethod.name}"
        +"fun $structName("
        indent {
            allocParams.forEach { +"$it," }
            +"alloc: ($allocLambdaParam) -> $resultType = $defaultAllocator,"
            +"init: context($resultType) $builderType.() -> Unit = {},"
        }
        "): $resultType" {
            "return alloc($allocLambdaArgument).also"(lambdaParams = "struct") {
                sTypeDefault(structClass, "struct.")
                +"init(struct, $builderType)"
            }
        }
        +""

    }

    context(KotlinFileBuilder)
    private fun processMembers(clazz: Class<*>, memberNames: Set<String>) {
        clazz.methods
            .asSequence()
            .filterNot { it.isStatic }
            .filter { it.name in memberNames }
            .map { it.toTypeString() }
            .groupBy { it.name }
            .forEach { (memberName, overloads) ->
                processDelegateMethod(clazz, memberName, overloads)
            }
    }

    context(KotlinFileBuilder)
    private fun processDelegateMethod(receiver: Class<*>, memberName: String, overloads: List<MethodTypeString>) {
        val getter = overloads.singleOrNull { it.nativeType != null && it.parameters.isEmpty() }
        val setter =
            overloads.singleOrNull { it.jvmType == it.element.declaringClass && it.parameters.singleOrNull()?.nativeType != null }?.parameters?.single()
        val typeString = getter ?: setter ?: return
        val category = featureSet.categoryByTypeName[typeString.type] ?: return
        val typeName = typeString.type
        if (typeString.isArray || typeString.numPointers > 0) {
            return
        }
        if (getter != null) {
            when (category) {
                Category.ENUM, Category.BITMASK -> {
                    val valsType = if (setter != null) "var" else "val"
                    +"""
                    $valsType ${receiver.fullName}.$memberName
                        get() = $typeName.of($memberName())
                    """
                    "        set(value) { $memberName(value.value) }"(
                        skipIf = setter == null,
                        keepIndent = true
                    )
                    +""
                }

                else -> Unit
            }
        }
        if (setter != null) {
            when (featureSet.categoryByTypeName[setter.type]) {
                Category.ENUM, Category.BITMASK -> {
                    usesContracts()
                    "inline fun ${receiver.fullName}.$memberName(builder: ${typeName}Builder.() -> $typeName):${receiver.fullName}" {
                        +"contract { callsInPlace(builder, InvocationKind.EXACTLY_ONCE) }"
                        +"return $memberName(builder(${typeName}Builder).value)"
                    }
                    +""
                    "fun ${receiver.fullName}.$memberName(value: $typeName):${receiver.fullName}" {
                        +"return $memberName(value.value)"
                    }
                    +""
                }

                Category.HANDLE -> {
                    if (LwjglClasses.vulkan(typeName) == null) {
                        "fun ${receiver.fullName}.$memberName(value: $typeName?):${receiver.fullName}" {
                            +"return $memberName(value?.handle ?: 0)"
                        }

                        +""
                        +"@JvmName(\"${memberName}NotNull\")"
                        "fun ${receiver.fullName}.$memberName(value: $typeName):${receiver.fullName}" {
                            +"return $memberName(value.handle)"
                        }
                        +""
                    }
                }

                else -> Unit
            }
        }
    }

    private fun writeStructAlias(alias: String, typeName: String) {
        val structClass = LwjglClasses.vulkan(alias)
        val bufferAlias = "$alias.Buffer"
        val bufferClass = LwjglClasses.vulkan(bufferAlias)
        if (structClass == null || bufferClass == null) return

        createKotlinFile(root, OUTPUT_PACKAGE, alias) {
            import(structClass)
            +"""
            typealias $alias = $typeName
            typealias $bufferAlias = $typeName.Buffer
            typealias ${alias}Builder = ${typeName}Builder
            typealias ${alias}BufferBuilder = ${typeName}BufferBuilder
            """
        }
    }

    context(KotlinFileBuilder)
    private fun sTypeDefault(onClass: Class<*>, receiver: String) {
        onClass.method("sType\$Default", returns = onClass)
            ?.let { +"$receiver`sType\$Default`()" }
    }
}

