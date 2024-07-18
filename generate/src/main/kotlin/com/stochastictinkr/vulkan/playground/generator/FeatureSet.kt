package com.stochastictinkr.vulkan.playground.generator

/**
 * A filtered view of the Vulkan API registry for a specific API, feature, and extension set.
 *
 * @param api The API to filter by. For example, "vulkan".
 * @param featureName The name of the feature to filter by. For example, "VK_VERSION_1_3".
 * @param registry The parsed Vulkan API registry.
 * @param featureClass The class which contains the static methods for the feature. For example, "VK13".
 * @param extensionClasses The classes which contain the static methods for the supported extensions.
 */
data class FeatureSet(
    val api: String,
    val featureName: String,
    val registry: Registry,
    val featureClass: Class<*>,
    val extensionClasses: Map<String, Class<*>?>,
    val documentation: Documentation
) {
    /**
     * The feature which this set is based on.
     */
    private val feature = registry.features.single { it.name == featureName }

    /**
     * The features which are required for this feature to be supported.
     */
    private val features =
        registry
            .features
            .associateBy { it.name }
            .let { byName -> generateSequence(feature) { byName[it.depends] } }
            .toList()
            .reversed()
            .asSequence()

    /**
     * The extensions which are supported by this feature set.
     */
    private val extensions =
        registry
            .extensions
            .asSequence()
            .filter { extensionSupported(it) }

    /**
     * Items which are removed by this feature set or its dependencies.
     */
    private val removes =
        features.flatMap { it.removes }
            .filter { it.api.isSupportedApi() }
            .plus(
                extensions
                    .flatMap { it.removes }
                    .filter { it.api.isSupportedApi() }
            )

    /**
     * The types which are removed by this feature set or its dependencies.
     */
    private val removedTypes by lazy {
        removes
            .flatMap { it.types }
            .map { it.name }
            .toSet()
    }

    /**
     * The commands which are removed by this feature set or its dependencies.
     */
    private val removedCommands by lazy {
        removes
            .flatMap { it.commands }
            .map { it.name }
            .toSet()
    }

    /**
     * The enums which are removed by this feature set or its dependencies.
     */
    private val removedEnums by lazy {
        removes
            .flatMap { it.enumsReferences }
            .map { it.name }
            .toSet()
    }

    /**
     * The items which are required by this feature set or its dependencies.
     */
    private val requires =
        features
            .flatMap { it.requires }
            .filter { requireSupported(it) }
            .plus(
                extensions
                    .flatMap { it.requires }
                    .filter { requireSupported(it) }
            )

    /**
     * The types which are required by this feature set or its dependencies.
     */
    val types: Sequence<Type> =
        requires
            .flatMap { it.types }
            .filter { typeReferenceSupported(it) }
            .map { type(it) }
            .flatMap { type ->
                sequence {
                    type.alias?.let { alias -> yield(type(alias)) }
                    yield(type)
                }
            }

    /**
     * Get the supported type with the given name.
     */
    private fun type(typeName: String) =
        registry.typesByName.getValue(typeName).single(::typeSupported)

    /**
     * Get the supported type with the given reference.
     */
    private fun type(typeReference: TypeReference) =
        type(typeReference.name)

    private fun typeOrNull(typeName: String?) =
        registry
            .typesByName[typeName]
            ?.singleOrNull { type -> type.api.isSupportedApi() }

    fun typeExists(typeName: String?) = registry.typesByName.containsKey(typeName)


    val categoryByTypeName = types.associate { it.name to it.category }

    private fun enumConstants(type: Type): Sequence<EnumConstant> =
        sequenceOf(
            registry
                .enumsByName.getValue(type.name)
                .values.asSequence(),

            requires
                .flatMap { it.inlineEnums }
                .filter { it.extends == type.name },
        )
            .flatten()
            .filter { constantSupported(it) }

    private val enumTypesByName = mutableMapOf<String, EnumType>()

    fun enumType(type: String): EnumType? {
        return typeOrNull(type)?.let(::enumType)
    }

    fun enumType(type: Type): EnumType {
        return enumTypesByName.getOrPut(type.name) { createEnumType(type) }
    }

    private fun createEnumType(type: Type): EnumType {
        val enumType = when (type.category) {
            Category.ENUM -> type
            Category.BITMASK -> {
                val values =
                    (type.details as BitmaskDetails).bitValues ?: type.requires ?: error("No values for bitmask $type")
                type(values)
            }

            else -> error("Unexpected category ${type.category}")
        }
        val bitWidth =
            registry
                .enumsByName[enumType.alias ?: enumType.name]
                ?.bitWidth
                ?: when (type.node.children("type").singleOrNull()?.textContent) {
                    "VkFlags" -> 32
                    "VkFlags64" -> 64
                    else -> error("Unable to determine bit width of ${type.name}")
                }

        return EnumType(
            name = type.name,
            alias = type.alias,
            typedef = type.node.children("type").singleOrNull()?.textContent,
            constantsCollection = enumType,
            isBitmask = type.category == Category.BITMASK,
            getValues = { enumConstants(enumType) },
            bitWidth
        )
    }

    private val supportedCommandNames = requires
        .flatMap { it.commands }
        .filter { commandReferenceSupported(it) }
        .map { it.name }
        .map { registry.commandsByName.getValue(it) }
        .filter { commandSupported(it) }
        .map { it.details.name }
        .toSet()

    private val supportedCommandSortOrder = supportedCommandNames
        .mapIndexed { index, name -> name to index }
        .toMap()


    val commandMethods =
        sequenceOf(listOf(featureClass), extensionClasses.values)
            .flatten()
            .filterNotNull()
            .flatMap { it.methods.asSequence() }
            .filter { it.isStatic }
            .filter { it.name in supportedCommandNames }
            .sortedBy { supportedCommandSortOrder.getValue(it.name) }
            .mapNotNull { method ->
                val command = registry.commandsByName[method.name] ?: return@mapNotNull null
                val commandDeclaration = when (val details = command.details) {
                    is CommandDeclaration -> details
                    is CommandAlias -> registry.commandsByName.getValue(details.alias).details as CommandDeclaration
                }
                val methodTypeString = method.toTypeString()
                val declaration = commandDeclaration.forApi()
                val autoSizedParams =
                    declaration.params
                        .filter { it.isAutoSize(declaration.params) }
                        .map { it.name }
                        .toSet()

                CommandMethod(
                    declaration,
                    methodTypeString,
                    matchedParams(methodTypeString, declaration, autoSizedParams),
                    autoSizedParams
                )
            }

    val handleTypes =
        types.filter { it.category == Category.HANDLE }.toList()

    fun handleDetails(handleType: String) =
        typeOrNull(handleType)
            ?.details as? HandleDetails
            ?: error("Type $handleType is not a handle")

    fun isHandle(type: String?): Boolean = categoryByTypeName[type] == Category.HANDLE

    private fun Collection<String>.isSupportedApi(): Boolean = isEmpty() || api in this

    private fun isSupportedDepends(@Suppress("UNUSED_PARAMETER") depends: String?): Boolean =
        true // TODO: Depends is an expression, not a simple string

    private fun extensionSupported(it: Extension): Boolean {
        return it.supported.isSupportedApi() && extensionClasses[it.name] != null
    }

    private fun requireSupported(it: Require): Boolean {
        return it.api.isSupportedApi() && isSupportedDepends(it.depends)
    }

    private fun typeSupported(it: Type): Boolean {
        return it.api.isSupportedApi()
    }

    private fun typeReferenceSupported(it: TypeReference): Boolean {
        return it.api.isSupportedApi() && it.name !in removedTypes
    }

    private fun commandReferenceSupported(it: CommandReference): Boolean {
        return it.name !in removedCommands
    }

    private fun commandSupported(it: Command): Boolean {
        return it.details.name !in removedCommands
    }

    private fun constantSupported(it: EnumConstant): Boolean = it.name !in removedEnums

    private fun CommandDeclaration.forApi(): CommandDeclaration {
        return copy(params = params.filter { it.api.isSupportedApi() })
    }

    fun sameType(left: String?, right: String?): Boolean {
        return left == right || left isAliasFor right || right isAliasFor left
    }

    private infix fun String?.isAliasFor(other: String?): Boolean {
        return this?.let { type(it).alias } == other
    }


    private fun matchedParams(
        methodTypeString: MethodTypeString,
        command: CommandDeclaration,
        autoSizedParams: Set<String>,
    ): List<MatchedParameter> {
        val declaredParams =
            command.params.filterNot { it.name in autoSizedParams }
        val parameters = methodTypeString.parameters
        if (declaredParams.size != parameters.size) {
            error(
                """
                |Parameter count mismatch for ${methodTypeString.name}: ${declaredParams.size} != ${parameters.size}
                |Declared: 
                ${declaredParams.joinToString("\n") { "|    ${it.name}: ${it.type} ${it.typeString}" }}
                |Method: 
                |${parameters.joinToString("\n") { "|    ${it.name}: ${it.type} ${it.typeString}" }}
                """.trimMargin()
            )
        }
        return declaredParams.zip(parameters)
            .map { (declaredParam, methodParam) ->
                validateParameter(declaredParam, methodParam)
                MatchedParameter(declaredParam, methodParam)
            }
    }

    private fun validateParameter(declaredParam: Param, methodParam: ParameterTypeString) {
        check(sameType(declaredParam.type, methodParam.type)) {
            "Declared parameter type ${declaredParam.type} does not match method parameter type ${methodParam.type}"
        }
        check(!declaredParam.isArray || methodParam.numPointers > 0) {
            "Declared parameter ${declaredParam.name} is an array but method parameter is not a pointer"
        }
    }

    private fun Param.isAutoSize(params: List<Param>): Boolean =
        params.any { it.len.contains(name) } && numPointers == 0
}
