package com.stochastictinkr.vulkan.playground.generator


@DslMarker
annotation class CommandMethodBuilderDsl

/**
 * A helper class for building a wrapper method around a Vulkan command.
 *
 * @param featureSet The feature set that the command belongs to.
 * @param methodType The LWJGL method type information.
 * @param methodName The name of the method to generate.
 * @param receiver The receiver type for the method, if any.
 * @param override Whether the method should be marked as an override.
 * @param reachableObjects The list of reachable objects.
 * @param reachableObjectsByType All reachable objects grouped by type.
 * @param returnType The return type of the method, if any.
 * @param returnStatement The return statement of the method, if any.
 * @param needsStack Whether wrapped calls need to be wrapped in an `onStack` block.
 * @param jvmName The JVM name of the method, if any.
 * @param inline Whether the method should be marked as inline.
 * @param evalWrapper Wraps the evaluation of the parameters and return statement.
 * @param skipContracts Whether to skip the contract block.
 * @param checkReturn A check for value returned by the method, if any.
 * @param ignoreReturn Whether to ignore the return value.
 * @param configure Additional configuration for the builder.
 * @property returnValueConstructor The constructor for the return value, if any.
 * @property parameters The list of parameters for the method.
 * @property arguments The list of arguments for the method.
 * @property lambdaArguments The list of argument names which are lambda arguments. Used for contract callsInPlace.
 * @property createVals The list of `val ...` statements to create within the eval block.
 *
 */
@CommandMethodBuilderDsl
class CommandMethodBuilder(
    val featureSet: FeatureSet,
    val methodType: MethodTypeString,
    var methodName: String,
    val receiver: String?,
    val override: Boolean,
    reachableObjects: List<ReachableObject>,
    val reachableObjectsByType: Map<String?, List<ReachableObject>>,
    val methodComment: String? = Thread.currentThread().stackTrace.getOrNull(2)
        ?.let { "generated from ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" },
    var returnType: String? = null,
    var returnStatement: String? = null,
    var needsStack: Boolean = false,
    private var jvmName: String? = null,
    var inline: Boolean? = null,
    var evalWrapper: KotlinFileBuilder.(KotlinFileBuilder.() -> Unit) -> Unit = { it() },
    var skipContracts: Boolean? = null,
    private var checkReturn: String? = null,
    var ignoreReturn: Boolean = returnType != null || returnStatement != null,
    configure: CommandMethodBuilder.() -> Unit = {},
) : KotlinWritable, Imports by ImportsImpl(mutableSetOf()) {

    var returnValueConstructor: ConstructorBuilder? = null
    private val reachableObjects = reachableObjects.toMutableList()
    val parameters = mutableListOf<String>()
    val arguments = mutableListOf<String>()
    val lambdaArguments = mutableListOf<String>()
    val createVals = mutableListOf<String>()

    init {
        configure()
        if (!ignoreReturn && methodType.type != "void" && returnStatement == null && returnType == null && returnValueConstructor == null) {
            val category = featureSet.categoryByTypeName[methodType.type]
            if (category == Category.ENUM || category == Category.BITMASK) {
                returnType = methodType.type
                returnStatement = "return ${methodType.type}(returnValue)"
            } else {
                import(methodType.jvmType)
                returnType = methodType.jvmType.fullName
                returnStatement = "return returnValue"
            }
        }
        if (methodType.type == "VkResult") {
            checkReturn = "VkResult(returnValue).reportFailure { \"${methodType.name}\" }"
        }
    }

    override fun KotlinFileBuilder.write() {
        val targetMethod = methodType.element
        val inline = if (inline ?: lambdaArguments.isNotEmpty()) "inline " else ""
        val skipContracts = inline.isEmpty() || skipContracts == true
        val override = if (override) "override " else ""
        val receiver = receiver?.let { "$it." } ?: ""
        val parameters = parameters.joinToString(", ")
        val arguments = arguments.joinToString(", ")
        val returnStatement = returnStatement ?: returnValueConstructor?.let { constructor ->
            "return ${constructor.name}(${constructor.arguments.joinToString(", ")})"
        }
        val returnType = (returnType ?: returnValueConstructor?.name)?.let { ": $it" } ?: ""

        this@KotlinFileBuilder.mergeImports(this@CommandMethodBuilder)

        jvmName?.let { +"@JvmName(\"$it\")" }

        methodComment?.let { +"// $it" }
        if (!skipContracts) usesContracts()
        "$inline${override}fun $receiver$methodName($parameters)$returnType" {
            "contract"(skipIf = skipContracts) {
                lambdaArguments..{ "callsInPlace($it, InvocationKind.EXACTLY_ONCE)" }
            }
            evalWrapper {
                if (needsStack) import("com.stochastictinkr.util.OnStack", "com.stochastictinkr.util.onStack")
                "onStack"(unwrapped = !needsStack) {
                    createVals..{ it }
                    import(targetMethod.declaringClass)
                    val returnValueString =
                        if (checkReturn != null || (returnStatement != null && targetMethod.returnType != Void.TYPE)) "val returnValue = " else ""
                    +"$returnValueString${targetMethod.declaringClass.fullName}.${targetMethod.name}($arguments)"
                    checkReturn?.let { +it }
                    returnStatement?.let { +it }
                }
            }
        }
    }

    /**
     * Adds a parameter to the method, and updates the reachable objects.
     * Handles special cases for different parameter types.
     */
    fun add(parameter: MatchedParameter) {
        if (parameter.isArray) {
            return passThroughParam(parameter)
        }
        reachableObjects.matchingOrNull(parameter)?.let {
            return addArgument(it.path)
        }
        when (featureSet.categoryByTypeName[parameter.type]) {
            Category.HANDLE -> inHandleParam(parameter)
            Category.ENUM, Category.BITMASK ->
                if (parameter.numPointers == 0) inEnumParam(parameter)
                else passThroughParam(parameter)

            Category.STRUCT, Category.UNION -> inStructParam(parameter)
            else -> passThroughParam(parameter)
        }
    }

    /**
     * Adds a return value constructor to the method.
     */
    @CommandMethodBuilderDsl
    fun thenConstruct(constructorName: String, configure: ConstructorBuilder.() -> Unit) =
        ConstructorBuilder(constructorName).also {
            returnValueConstructor = it
            it.configure()
        }

    /**
     * A builder for creating a constructor call.
     */
    inner class ConstructorBuilder(val name: String) {
        /**
         * The list of arguments for the constructor.
         */
        val arguments = mutableListOf<String>()

        /**
         * Adds a reachable argument to the constructor call from the parameters.
         */
        fun add(parameter: LwjglTypeString<*>) {
            add(parameter.jvmType, parameter.type)
        }

        /**
         * Adds a reachable argument to the constructor call with the given type.
         */
        fun add(jvmType: Class<*>?, type: String) {
            val selfReachable = reachableObjects.matchingOrNull(jvmType, type)
                ?: error("Parameter of $type with ${jvmType?.fullName ?: "null"} jvm type not found in reachable objects for $methodName")
            arguments.add(selfReachable.path)
        }

        /**
         * Adds an argument expression to the constructor call.
         */
        fun addArgument(argument: String) {
            arguments.add(argument)
        }
    }

    /**
     * Adds an argument expression to the method call.
     */
    private fun addArgument(expression: String) {
        arguments.add(expression)
    }

    /**
     *  Add a struct input parameter to the method and method call.
     */
    private fun inStructParam(parameter: MatchedParameter) {
        addReachable(parameter.asReachable())
        passThroughParam(parameter)
    }

    /**
     * Add an enum input parameter to the method and method call.
     * The parameter is received as a lambda builder, and the value is extracted from the builder.
     */
    private fun inEnumParam(parameter: MatchedParameter) {
        check(parameter.jvmType.isPrimitive) { "Enum parameter must be a primitive type in $methodName" }

        val optional = if (parameter.optional == listOf(true)) "= { of(0) }" else ""
        parameters.add("${parameter.name}Builder: ${parameter.type}Builder.()->${parameter.type}$optional")
        lambdaArguments.add("${parameter.name}Builder")
        createVals.add("val ${parameter.name} = ${parameter.name}Builder(${parameter.type}Builder)")
        addReachable(parameter.asReachable(jvmType = null))
        val valuePath = "${parameter.name}.value"
        addReachable(parameter.asReachable(path = valuePath))
        addArgument(valuePath)
    }

    /**
     * Add a handle input parameter to the method and method call.
     * The parameter is received as the wrapped handle, and the value is extracted from the handle,
     * depending on the jvm type the method expects.
     */
    private fun inHandleParam(param: MatchedParameter) {
        val className = HandleGenerator.handleClassName(param.type)
        var newReachableObjects =
            reachableObjectsByType.getValue(param.type)
                .map { it.rootedAt(param.name) }

        if (param.optional == listOf(true)) {
            newReachableObjects = newReachableObjects.map { it.optional() }
        }

        newReachableObjects
            .matchingOrNull(param)
            ?.let { paramReachable ->
                LwjglClasses.vulkan(param.type)?.let { import(it) }
                var nullable = ""
                if (param.optional == listOf(true)) {
                    nullable = "?"
                }
                parameters.add("${param.name}: $className$nullable")
                addArgument(paramReachable.path)
                reachableObjects.addAll(newReachableObjects)
            }
            ?: passThroughParam(param)
    }

    /**
     * adds a parameter to the method and method call, passing through the parameter directly.
     */
    private fun passThroughParam(parameter: LwjglTypeString<*>) {
        import(parameter.jvmType)
        var nullable = ""
        var default = ""
        if (parameter.isNullable) {
            nullable = "?"
            if (parameter.jvmType != CharSequence::class.java) {
                default = " = null"
            }        }
        parameters.add("${parameter.name}: ${parameter.jvmType.fullName}$nullable$default")
        addArgument(parameter.name)
    }

    /**
     * Creates a new reachable object from a parameter.
     */
    private fun MatchedParameter.asReachable(
        path: String = name,
        typeName: String = type,
        jvmType: Class<*>? = this.jvmType,
    ) = ReachableObject(false, path, typeName, jvmType)

    /**
     * Adds a reachable object to the list of reachable objects.
     */
    fun addReachable(element: ReachableObject) {
        reachableObjects.add(element)
    }
}