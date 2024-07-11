package com.stochastictinkr.vulkan.playground.generator

import java.nio.file.Path
import kotlin.math.absoluteValue

/**
 * Generates Kotlin code for Vulkan enums and bitmask types.
 */
class EnumGenerator(private val root: Path, featureSet: FeatureSet) {
    val registry = featureSet.registry
    private val tagPattern = registry.tags.flatMap { it.tag }.joinToString("|") { it.name }
    private val tagSuffix = Regex("$tagPattern\$")

    fun generateEnumFile(collection: EnumType) {
        val className = collection.name

        collection.alias?.let { aliasName ->
            createKotlinFile(root, OUTPUT_PACKAGE, className) {
                +"typealias $className = $aliasName"
                +"typealias ${className}Builder = ${aliasName}Builder"
            }
            return
        }

        createKotlinFile(root, OUTPUT_PACKAGE, className) {

            val formatValue = collection.valueFormatter
            val valueType = collection.valueType

            +"/**"
            +" * A strongly-typed ${if (collection.isBitmask) "bitmask" else "enum"} representing the $className type."
            +" */"
            +"@JvmInline"
            "value class $className(val value: $valueType = 0)" {
                if (className == "VkResult") vkResultMembers()
                if (collection.isBitmask) bitmaskMembers(className, collection)

                "companion object" {
                    +"""
                    /**
                     * Convenience function to create a $className instance. The lambda is in the context of the builder,
                     * so you can use the constant names directly, or use the `of` function to create a value with a
                     * specific numeric value.
                     */
                    """
                    // usesContracts()
                    +"""
                    inline operator fun invoke(init: Companion.()->$className): $className {
                        // contract { callsInPlace(this, InvocationKind.EXACTLY_ONCE) }
                        // Contracts are not supported for operator functions yet. 
                        return this.init()
                    }
                    """
                    +"""
                    /**
                     * Creates a $className instance with the given value.
                     */
                    """
                    +"fun of(value: $valueType) = $className(value)"

                    constantDeclarations(collection, className, formatValue)

                }
                if (collection.isBitmask)
                    writeBitmaskGetName(collection, formatValue)
                else writeEnumGetName(collection)

                +"override fun toString() = \"$className(\$name)\""
            }
            +""
            +"typealias ${className}Builder = $className.Companion"
        }
    }

    context(KotlinFileBuilder)
    private fun writeBitmaskGetName(
        collection: EnumType,
        formatValue: (num: Long, startPad: Boolean) -> String,
    ) {
        +"/**"
        +" * Returns a string representation of the bitmask value. The string is a pipe-separated list of"
        +" * constant names, with an optional hex value for any unknown bits."
        +" * For each known set bit, the corresponding constant name is included in the list."
        +" */"
        "val name: String get()" {
            +"var remaining = value"
            "return sequence<String>()" {
                collection
                    .values
                    .mapNotNull { it.bitPos?.to(it.name) }
                    .distinctBy { (bit) -> bit }
                    .sortedBy { (bit) -> bit }
                    .forEach { (bit, name) ->
                        val mask = formatValue(1L shl bit, false)
                        +"""
                    if ((value and $mask) == $mask) {
                        yield("$name")
                        remaining -= $mask
                    }
                    """
                    }
                val suffix = if (collection.valueType == "Long") "L" else ""
                "if (remaining != 0$suffix)" {
                    +"yield(\"0x\${remaining.toString(16)}\")"
                }
            }
            -"    .joinToString(\"|\")"
        }
    }

    context(KotlinFileBuilder)
    private fun writeEnumGetName(
        collection: EnumType,
    ) {
        +"/**"
        +" * Returns a string representation of the enum value."
        +" * If the value is a known constant, the constant name is returned, otherwise the value is returned as"
        +" * a decimal string."
        +" */"
        "val name:String get()" {
            "return when (value)" {
                val valueWidth = collection
                    .values
                    .mapNotNull { it.longValue?.toString() }
                    .maxOfOrNull { it.length } ?: 0
                collection
                    .values
                    .filter { it.longValue != null }
                    .distinctBy { it.longValue }
                    .sortedBy { constantSortOrder(it) }
                    .forEach {
                        val valueString =
                            if (it.bitPos != null) {
                                "0x${it.longValue?.toString(16)}"
                            } else {
                                it.longValue.toString()
                            }
                        val suffix = if (collection.valueType == "Long") "L" else ""
                        -"${valueString.padStart(valueWidth)}$suffix -> \"${it.name}\""
                    }
                +"else -> value.toString()"
            }
        }
    }

    private val EnumType.valueFormatter: (num: Long, startPad: Boolean) -> String
        get() {
            val valueRadix = if (isBitmask) 16 else 10
            val valueSuffix = if (valueType == "Long") "L" else ""
            val hasNegatives = values.any { (it.longValue ?: 0) < 0 }
            val valueWidth =
                (values
                    .mapNotNull { it.longValue?.toString(valueRadix) }
                    .maxOfOrNull { it.length } ?: 0) + valueSuffix.length

            if (isBitmask) {
                return { num, _ ->
                    val signPlace = if (hasNegatives) {
                        if (num >= 0) " " else "-"
                    } else ""
                    "${signPlace}0x${num.toString(16).padStart(valueWidth, '0')}$valueSuffix"
                }
            } else {
                return { num, padStart ->
                    if (padStart) "$num$valueSuffix".padStart(valueWidth)
                    else "$num$valueSuffix".padEnd(valueWidth)
                }
            }
        }

    private fun constantSortOrder(it: EnumConstant): Long? {
        // For aesthetic reasons, we want to sort the constants in a specific order
        // This is purely for readability and has no effect on the generated code.
        val value = it.longValue ?: return null
        val absoluteValue = value.absoluteValue
        return if (absoluteValue > 1000) {
            // It is an extension, sort by absolute value to group them together
            absoluteValue
        } else {
            // It is not an extension, positive values first, ascending, then negative values descending
            // EG: 1, 2, 3, -1, -2, -3
            if (value < 0) -value else value - 1000
        }
    }



    context(KotlinFileBuilder)
    private fun constantDeclarations(
        collection: EnumType,
        className: String,
        formatValue: (num: Long, startPad: Boolean) -> String,
    ) {
        val classTag = tagSuffix.find(collection.name)?.value ?: ""
        val removablePrefix = getRemovablePrefix(className, classTag)

        val constantDeclarations =
            getConstantDeclarations(classTag, removablePrefix, collection, formatValue)

        val nameWidth = constantDeclarations.maxOfOrNull { (name) -> name.length } ?: 0

        +"// $removablePrefix(*) constants"
        constantDeclarations..{ (name, init) ->
            "val ${name.padEnd(nameWidth)} = $init"
        }
    }

    private fun getRemovablePrefix(className: String, classTag: String): String {
        val prefix =
            if (className == "VkResult") "VK_"
            else className
                .removeSuffix(classTag)
                .replace(Regex("(?:FlagBits|Flags)(\\d*)$")) { it.groups[1]?.value ?: "" }

        val snakePrefix = prefix.toSnakeUpperCase() + '_'
        return snakePrefix
    }

    private fun getConstantDeclarations(
        classTag: String,
        removablePrefix: String,
        collection: EnumType,
        formatValue: (num: Long, startPad: Boolean) -> String,
    ): List<Pair<String, String>> {
        fun String.cleanSuffix() =
            removeSuffix("_$classTag").replace(Regex("_BIT(_($tagPattern))?$"), "\$1")

        fun String.cleanedName(): String {
            val cleaned = removePrefix(removablePrefix).cleanSuffix()
            return if (cleaned.first().isDigit()) "`$cleaned`" else cleaned
        }
        val (aliased, valued) = collection.values.partition { it.alias != null }
        val valueNames = valued.associate { it.name to it.name.cleanedName() }
        return sequenceOf(
            valued
                .sortedBy { constantSortOrder(it) }
                .map { it.name.cleanedName() to "of(${formatValue(it.longValue!!, false)})" },
            aliased
                .filter { it.alias in valueNames }
                .sortedBy { it.name }
                .map { it.name.cleanedName() to valueNames.getValue(it.alias!!) }
        ).flatten()
            .distinct()
            .filter { (name, value) -> name != value }
            .toList()
    }

    context(KotlinFileBuilder)
    private fun vkResultMembers() {
        +"""
        /**
         * Returns true if the result is not an error.
         */
        val isSuccess get() = value >= 0
                   
        /**
         * Returns true if the result is an error.
         */
        val isError get() = value < 0
                   
        /**
         * Returns true if the result is a warning.
         */
        val isWarning get() = value > 0
                   
        /**
         * Throws an exception if the result is an error. The exception message includes the name of the result.
         */
        fun reportFailure() = check(isSuccess) { "Failed with ${'$'}name" }
                   
        /**
         * Throws an exception if the result is an error, prefixed with the given message. The exception message
         * includes the name of the result.
         */
        inline fun reportFailure(lazyMessage: () -> String) = check(isSuccess) { "${'$'}{lazyMessage()}: Failed with ${'$'}name" }
                   
        /**
         * Throws an exception if the result is an error, prefixed with the given message. The exception message
         * includes the name of the result.
         */
        fun reportFailure(message: String) = reportFailure { message }
        """
    }

    context(KotlinFileBuilder)
    private fun bitmaskMembers(className: String, collection: EnumType) {
        +"""
        /**
         * Returns a union of this bitmask and another bitmask.
         */
        operator fun plus(other: $className) = $className(value or other.value)
        
        /**
         * Returns a bitmask that has all the bits set in this bitmask but not in the other bitmask.
         */
        operator fun minus(other: $className) = $className(value and other.value)
        
        /**
         * Returns true if this bitmask contains all the bits in the other bitmask.
         */
        operator fun contains(other: $className) = value and other.value == other.value
        """
        """
        /**
         * Returns true if this bitmask contains the given bit.
         */
        operator fun contains(bit: collection.constantsCollection.name) = value and other.value == other.value
         
        """(skipIf = collection.constantsCollection.name == collection.name)
    }
}
