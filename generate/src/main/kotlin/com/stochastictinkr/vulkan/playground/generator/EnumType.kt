package com.stochastictinkr.vulkan.playground.generator

/**
 * Details of an enumeration type, including its constants.
 *
 * @param name The name of the enumeration type.
 * @param typedef The typedef of the enumeration type, if any.
 * @param constantsCollection The type which contains the enumeration constants, if any.
 * @param isBitmask Whether the enumeration type is a bitmask.
 * @param getValues The function which returns the enumeration constants.
 *
 * @property values The enumeration constants.
 */
class EnumType(
    val name: String,
    val typedef: String?,
    val constantsCollection: Type?,
    val isBitmask: Boolean,
    private val getValues: () -> Sequence<EnumConstant>,
    val bitWidth: Int,
) {
    val values get() = getValues()
    val valueType =
        when (bitWidth) {
            32 -> "Int"
            64 -> "Long"
            else -> error("Unexpected bit width")
        }
}