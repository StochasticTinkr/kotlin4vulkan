package com.stochastictinkr.vulkan.playground.generator

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import java.nio.file.Path
import kotlin.io.path.isDirectory

const val OUTPUT_PACKAGE = "com.stochastictinkr.vulkan"

/**
 * Generates Kotlin code for a feature set.
 *
 * @param outputDir The path to the output directory for the generated files.
 * @param featureSet The feature set to generate bindings for.
 */
fun generate(
    outputDir: Path,
    featureSet: FeatureSet,
) {
    val structGenerator = StructGenerator(outputDir, featureSet)
    val enumGenerator = EnumGenerator(outputDir, featureSet)
    val handleGenerator = HandleGenerator(outputDir, featureSet)

    // Generate the global Vulkan file
    handleGenerator.generateHandleFile(null, null)

    // Generate the files for each supported type
    featureSet.types.processList("type", { "${"${it.category}:".padEnd(13)} ${it.name}" }) { type ->
        when (type.category) {
            Category.HANDLE -> handleGenerator.generateHandleFile(type, type.details as HandleDetails)
            Category.ENUM -> enumGenerator.generateEnumFile(featureSet.enumType(type))
            Category.STRUCT, Category.UNION ->
                structGenerator.generateStructFile(type, type.details as StructDetails)

            else -> {}
        }
    }
}

private fun createFeatureSet(
    registry: Registry,
    featureName: String,
    api: String,
    documentation: Documentation,
): FeatureSet {
    fun String.extensionToClassName(): String {
        return replace("VK_", "")
            .splitToSequence("_")
            .map { it.replaceFirstChar(Char::uppercase) }
            .joinToString("")
    }

    val extensionClasses =
        registry.extensions
            .map { it.name }
            .associateWith { LwjglClasses.vulkan(it.extensionToClassName()) }

    val featureClassName = featureName.replace("_VERSION_", "").replace("_", "")
    val featureClass = LwjglClasses.vulkan(featureClassName) ?: error("Missing $featureClassName class")
    return FeatureSet(api, featureName, registry, featureClass, extensionClasses, documentation)
}


class Arguments(parser: ArgParser) {
    val input: Path by parser.storing("Path to the Vulkan-Docs directory") { Path.of(this) }
    val output: Path by parser.storing("Path to the output directory") { Path.of(this) }
    val targetFeature: String by parser.storing("Target feature name").default("VK_VERSION_1_3")
    val targetApi: String by parser.storing("Target API name").default("vulkan")
}

fun main(vararg args: String) {
    val arguments = ArgParser(args).parseInto(::Arguments)
    if (!arguments.input.isDirectory()) error("Input is not a directory")
    println("Generating bindings for ${arguments.targetApi} ${arguments.targetFeature} in ${arguments.output} from ${arguments.input}")
    val featureSet = with(arguments) {
        val registry = parseRegistry(loadXml(input.resolve("xml/vk.xml")))
        val documentation = parseDocumentation(input)
        createFeatureSet(registry, targetFeature, targetApi, documentation)
    }
    generate(arguments.output, featureSet)
}
