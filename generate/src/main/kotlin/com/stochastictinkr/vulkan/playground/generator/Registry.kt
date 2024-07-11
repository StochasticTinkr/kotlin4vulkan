package com.stochastictinkr.vulkan.playground.generator

import org.w3c.dom.Document
import org.w3c.dom.Node

data class Registry(
    val platforms: List<Platforms>,
    val tags: List<Tags>,
    val types: List<Types>,
    val enums: List<Enums>,
    val commands: List<Commands>,
    val features: List<Feature>,
    val extensions: List<Extension>,
) {
    val typesByName = types.flatMap { it.type }.groupBy { it.name }
    val enumsByName = enums.associateBy { it.name }
    val commandsByName = commands.flatMap { it.command }.associateBy { it.details.name }

}

data class Platforms(
    val platform: List<Platform>,
)

data class Platform(
    val name: String,
    val protect: String,
)

data class Tags(
    val tag: List<Tag>,
)

data class Tag(
    val name: String,
    val author: String,
    val contact: String,
)

data class Types(
    val type: List<Type>,
)

enum class Deprecation {
    UNDEPRECATED,
    DEPRECATED,
    ALIASED,
    IGNORED,
    ;

    companion object {
        fun fromNode(node: Node) = when (node.attribute("deprecated")) {
            "deprecated" -> DEPRECATED
            "aliased" -> ALIASED
            "ignored" -> IGNORED
            else -> UNDEPRECATED
        }
    }
}

sealed interface TypeDetails
enum class Category {
    BITMASK,
    DEFINE,
    ENUM,
    FUNC_POINTER,
    GROUP,
    HANDLE,
    INCLUDE,
    STRUCT,
    UNION,
    BASETYPE,
    NO_CATEGORY,
    ;

    companion object {
        fun fromString(it: String?) = when (it) {
            "bitmask" -> BITMASK
            "define" -> DEFINE
            "enum" -> ENUM
            "funcpointer" -> FUNC_POINTER
            "group" -> GROUP
            "handle" -> HANDLE
            "include" -> INCLUDE
            "struct" -> STRUCT
            "union" -> UNION
            "basetype" -> BASETYPE
            null -> NO_CATEGORY
            else -> error("Unknown category $it")
        }

        fun fromNode(node: Node) = fromString(node.attribute("category"))
    }
}

data class Type(
    val node: Node,
    val requires: String?,
    val name: String,
    val alias: String?,
    val api: List<String>,
    val category: Category,
    val details: TypeDetails,
    val text: String,
)

data object NoTypeDetails : TypeDetails

data class BitmaskDetails(
    val bitValues: String?,
) : TypeDetails

data class HandleDetails(
    val parent: String?,
    val dispatchable: Boolean?,
    val alias: String?,
) : TypeDetails

data class StructDetails(
    val members: List<Member>,
    val returnedOnly: Boolean,
) : TypeDetails

data class Member(
    val api: List<String>,
    val values: List<String>,
    val len: List<String>,
    val altLen: List<String>,
    val deprecated: Deprecation,
    val externSync: Boolean,
    override val optional: List<Boolean>,
    val selector: String?,
    val selection: String?,
    val objectType: String?,
    val stride: String?,
    val text: String,
    override val name: String,
    override val type: String?,
    override val typeString: String,
    val enum: String?,
) : TypeString {
    override val isOutput = false
}

enum class EnumCategory {
    BITMASK,
    ENUM,
    CONSTANTS,
}

data class Enums(
    val name: String?,
    val type: EnumCategory,
    val bitWidth: Int,
    val values: List<EnumValue>,
)

sealed interface EnumConstant {
    val value: String?
    val bitPos: Int?
    val name: String
    val alias: String?
    val type: String?
    val longValue: Long?
}

private fun EnumConstant.parseLongValue(): Long? {
    val bit = bitPos
    if (bit != null) return 1L shl bit
    val value = value
    if (value != null) return when {
        value.startsWith("0x") -> value.substring(2).toLong(16)
        value.startsWith("0") -> value.toLong(8)
        else -> value.toLong()
    }
    return null
}


data class EnumValue(
    override val name: String,
    override val value: String?,
    override val bitPos: Int?,
    val api: List<String>,
    val deprecated: Deprecation,
    override val type: String?,
    override val alias: String?,
) : EnumConstant {
    override val longValue: Long? by lazy {
        if (alias != null) null
        else parseLongValue() ?: error("Unable to determine value for $name")
    }
}


data class Commands(
    val command: List<Command>,
)

enum class Task {
    ACTION,
    SYNCRONIZATION,
    STATE,
    INDIRECTION,
    ;

    companion object {
        fun fromString(it: String) = when (it) {
            "action" -> ACTION
            "synchronization" -> SYNCRONIZATION
            "state" -> STATE
            "indirection" -> INDIRECTION
            else -> error("Unknown task $it")
        }
    }
}

enum class Queue {
    COMPUTE,
    DECODE,
    ENCODE,
    GRAPHICS,
    TRANSFER,
    SPARSE_BINDING,
    OPTICAL_FLOW,
    ;

    companion object {
        fun fromString(it: String) = when (it) {
            "compute" -> COMPUTE
            "decode" -> DECODE
            "encode" -> ENCODE
            "graphics" -> GRAPHICS
            "transfer" -> TRANSFER
            "sparse_binding" -> SPARSE_BINDING
            "opticalflow" -> OPTICAL_FLOW
            else -> error("Unknown queue $it")
        }
    }
}

enum class Scope {
    INSIDE,
    OUTSIDE,
    BOTH,
    ;

    companion object {
        fun fromNode(node: Node, attributeName: String) = when (node.attribute(attributeName)) {
            "inside" -> INSIDE
            "outside" -> OUTSIDE
            "both" -> BOTH
            null -> null
            else -> error("Unknown scope ${node.attribute(attributeName)}")
        }
    }
}

enum class CommandBufferLevel {
    PRIMARY,
    SECONDARY,
    ;

    companion object {
        fun fromNode(node: Node): CommandBufferLevel? {
            return when (node.attribute("cmdBufferLevel")) {
                "primary" -> PRIMARY
                "secondary" -> SECONDARY
                null -> null
                else -> error("Unknown command buffer level: ${node.attribute("cmdBufferLevel")}")
            }
        }
    }
}


sealed interface CommandDetails {
    val name: String
}

data class Command(
    val api: List<String>,
    val details: CommandDetails,
    val description: String?,
)

interface TypeString {
    val name: String
    val type: String?
    val typeString: String
    val optional: List<Boolean> get() = emptyList()
    val isOutput get() = !isConst && numPointers > 0
}

val TypeString.isConst get() = typeString.trim().startsWith("const")
val TypeString.isArray get() = typeString.contains('[')
val TypeString.numPointers get() = typeString.count { it == '*' }


data class Proto(
    val text: String,
    override val type: String?,
    override val name: String,
    override val typeString: String,
) : TypeString

data class Param(
    val api: List<String>,
    val values: List<String>,
    override val optional: List<Boolean>,
    val len: List<String>,
    val altLen: List<String>,
    val externSync: Boolean,
    val selector: String?,
    val selection: String?,
    val objectType: String?,
    val text: String,
    override val name: String,
    override val type: String?,
) : TypeString {
    override val typeString: String get() = text
}

data class CommandAlias(
    val alias: String,
    override val name: String,
) : CommandDetails

data class CommandDeclaration(
    val proto: Proto,
    val params: List<Param>,
    val tasks: List<Task>,
    val queues: List<Queue>,
    val successCodes: List<String>,
    val errorCodes: List<String>,
    val renderPass: Scope?,
    val videoCoding: Scope?,
    val commandBufferLevel: CommandBufferLevel?,
) : CommandDetails {
    override val name: String get() = proto.name
}

data class Feature(
    val api: List<String>,
    val name: String,
    val number: String?,
    val depends: String?,
    val sortOrder: Int?,
    val protect: String?,
    val requires: List<Require>,
    val removes: List<Remove>,
)

data class Extension(
    val name: String,
    val number: Int,
    val sortOrder: Int?,
    val author: String?,
    val contact: String?,
    val type: String?,
    val depends: String?,
    val protect: String?,
    val platform: List<String>,
    val supported: List<String>,
    val ratified: String?,
    val promotedTo: String?,
    val deprecatedBy: String?,
    val obsoleteBy: String?,
    val provisional: Boolean,
    val specialUse: String?,

    val requires: List<Require>,
    val removes: List<Remove>,
)


data class Require(
    val profile: String?,
    val api: List<String>,
    val depends: String?,
    val commands: List<CommandReference>,
    val enumsReferences: List<EnumReference>,
    val inlineEnums: List<InlineEnum>,
    val types: List<TypeReference>,
)

data class CommandReference(
    val name: String,
)

data class EnumReference(
    val name: String,
    val api: List<String>,
)

data class InlineEnum(
    override val name: String,
    override val type: String?,
    override val value: String?,
    override val bitPos: Int?,
    val offset: Int?,
    val dir: Boolean,
    val extends: String?,
    val extensionNumber: Int?,
    override val alias: String?,
    val protect: String?,
) : EnumConstant {
    override val longValue: Long? by lazy {
        when {
            alias != null -> null
            extensionNumber != null && offset != null -> {
                val number = (1000000000L + (extensionNumber - 1) * 1000L) + offset
                if (dir) -number else number
            }

            else -> parseLongValue() ?: error("Unable to determine value for $name")
        }
    }

}

data class TypeReference(
    val name: String,
    val api: List<String>,
)


data class Remove(
    val profile: String?,
    val api: List<String>,
    val commands: List<CommandReference>,
    val enumsReferences: List<EnumReference>,
    val types: List<TypeReference>,
)

fun parseRegistry(document: Document): Registry =
    parseRegistry(document.documentElement)

private fun parseRegistry(node: Node): Registry = Registry(
    platforms = node.children("platforms").map { parsePlatforms(it) }.toList(),
    tags = node.children("tags").map { parseTags(it) }.toList(),
    types = node.children("types").map { parseTypes(it) }.toList(),
    enums = node.children("enums").map { parseEnums(it) }.toList(),
    commands = node.children("commands").map { parseCommands(it) }.toList(),
    features = node.children("feature").map { parseFeature(it) }.toList(),
    extensions = node.children("extensions")
        .flatMap { it.children("extension") }
        .map { parseExtension(it) }.toList(),
)

private fun parsePlatforms(node: Node): Platforms = Platforms(
    platform = node.children("platform").map { parsePlatform(it) }.toList(),
)

private fun parsePlatform(node: Node): Platform = Platform(
    name = node.attribute("name") ?: error("Missing name attribute"),
    protect = node.attribute("protect") ?: error("Missing protect attribute"),
)

private fun parseTags(node: Node): Tags = Tags(
    tag = node.children("tag").map { parseTag(it) }.toList(),
)

private fun parseTag(node: Node): Tag = Tag(
    name = node.attribute("name")!!,
    author = node.attribute("author")!!,
    contact = node.attribute("contact")!!,
)

private fun parseTypes(node: Node): Types = Types(
    type = node.children("type").map { parseType(it) }.toList(),
)

private fun parseType(node: Node): Type {
    val name =
        node.attribute("name")
            ?: node.children("name").singleOrNull()?.textContent
            ?: error("Missing name attribute")
    val category = Category.fromNode(node)
    return Type(
        node = node,
        requires = node.attribute("requires"),
        name = name,
        alias = node.attribute("alias"),
        api = node.attribute("api")?.split(",") ?: emptyList(),
        category = category,
        details = when (category) {
            Category.BITMASK -> BitmaskDetails(node.attribute("bitvalues"))
            Category.HANDLE -> HandleDetails(
                parent = node.attribute("parent"),
                dispatchable = when (node.children("type").singleOrNull()?.textContent) {
                    "VK_DEFINE_HANDLE" -> true
                    "VK_DEFINE_NON_DISPATCHABLE_HANDLE" -> false
                    null -> if (node.hasAttribute("alias")) null else error("Missing handle type")
                    else -> error("Unknown handle type")
                },
                alias = node.attribute("alias"),
            )

            Category.STRUCT -> StructDetails(
                members = node.children("member").map { parseMember(it, name) }.toList(),
                returnedOnly = node.attribute("returnedonly") == "true",
            )

            Category.UNION -> StructDetails(
                members = node.children("member").map { parseMember(it, name) }.toList(),
                returnedOnly = node.attribute("returnedonly") == "true",
            )

            else -> NoTypeDetails
        },
        text = node.textContent,
    )
}


private fun parseMember(node: Node, name: String): Member = Member(
    api = node.attribute("api")?.split(",") ?: emptyList(),
    values = node.attribute("values")?.split(",") ?: emptyList(),
    len = node.attribute("len")?.split(",") ?: emptyList(),
    altLen = node.attribute("altlen")?.split(",") ?: emptyList(),
    deprecated = Deprecation.fromNode(node),
    externSync = node.attribute("externsync") == "true",
    optional = node.attribute("optional")?.split(",")?.map { it == "true" } ?: emptyList(),
    selector = node.attribute("selector"),
    selection = node.attribute("selection"),
    objectType = node.attribute("objecttype"),
    stride = node.attribute("stride"),
    text = node.textContent,
    name = node.children("name").singleOrNull()?.textContent ?: error("$name member missing name"),
    type = node.children("type").singleOrNull()?.textContent,
    typeString = node.textExcludingName,
    enum = node.children("enum").singleOrNull()?.textContent,
)

private fun parseEnums(node: Node): Enums {
    val name = node.attribute("name")
    return Enums(
        name = name,
        type = when (node.attribute("type")) {
            "bitmask" -> EnumCategory.BITMASK
            "enum" -> EnumCategory.ENUM
            "constants" -> EnumCategory.CONSTANTS
            else -> error("Unknown enum type")
        },
        bitWidth = node.attribute("bitwidth")?.toInt() ?: 32,
        values = node.children("enum").map { parseEnumValue(it, name) }.toList(),
    )
}

private fun parseEnumValue(node: Node, name: String?): EnumValue = EnumValue(
    name = node.attribute("name") ?: error("Enum value in $name is name attribute."),
    value = node.attribute("value"),
    bitPos = node.attribute("bitpos")?.toInt(),
    api = node.attribute("api")?.split(",") ?: emptyList(),
    deprecated = Deprecation.fromNode(node),
    type = node.attribute("type"),
    alias = node.attribute("alias"),
)

private fun parseCommands(node: Node): Commands = Commands(
    command = node.children("command").map { parseCommand(it) }.toList(),
)

private fun parseCommand(node: Node): Command = Command(
    api = node.attribute("api")?.split(",") ?: emptyList(),
    details = parseCommandDeclaration(node) ?: parseCommandAlias(node),
    description = node.children("description").singleOrNull()?.textContent,
)

private fun parseCommandAlias(node: Node) = CommandAlias(
    alias = node.attribute("alias") ?: error("Command is neither alias nor declaration"),
    name = node.attribute("name") ?: error("Command is missing name"),
)

private fun parseCommandDeclaration(node: Node) =
    node
        .children("proto")
        .singleOrNull()
        ?.let { proto ->
            CommandDeclaration(
                tasks = node.attribute("tasks")?.split(",")?.map { Task.fromString(it) } ?: emptyList(),
                queues = node.attribute("queues")?.split(",")?.map { Queue.fromString(it) } ?: emptyList(),
                successCodes = node.attribute("successcodes")?.split(",") ?: emptyList(),
                errorCodes = node.attribute("errorcodes")?.split(",") ?: emptyList(),
                renderPass = Scope.fromNode(node, "renderpass"),
                videoCoding = Scope.fromNode(node, "videocoding"),
                commandBufferLevel = CommandBufferLevel.fromNode(node),
                proto = Proto(
                    text = proto.textContent,
                    type = proto.children("type").singleOrNull()?.textContent,
                    name = proto.children("name").singleOrNull()?.textContent ?: error("Command is missing name"),
                    typeString = proto.textExcludingName,
                ),
                params = node.children("param").map { parseParam(it) }.toList(),
            )
        }

private val Node.textExcludingName get() = children.filterNot { it.nodeName == "name" }.joinToString(" ") { it.textContent }

private fun parseParam(node: Node): Param = Param(
    api = node.attribute("api")?.split(",") ?: emptyList(),
    values = node.attribute("values")?.split(",") ?: emptyList(),
    optional = node.attribute("optional")?.split(",")?.map { it == "true" } ?: emptyList(),
    len = node.attribute("len")?.split(",") ?: emptyList(),
    altLen = node.attribute("altlen")?.split(",") ?: emptyList(),
    externSync = node.attribute("externsync") == "true",
    selector = node.attribute("selector"),
    selection = node.attribute("selection"),
    objectType = node.attribute("objecttype"),
    text = node.textContent,
    name = node.children("name").singleOrNull()?.textContent ?: error("Param is missing name"),
    type = node.children("type").singleOrNull()?.textContent,
)

private fun parseFeature(node: Node): Feature {
    val name = node.attribute("name") ?: error("Missing name attribute")
    return Feature(
        api = node.attribute("api")?.split(",") ?: emptyList(),
        name = name,
        number = node.attribute("number"),
        depends = node.attribute("depends"),
        sortOrder = node.attribute("sortorder")?.toInt(),
        protect = node.attribute("protect"),
        requires = node.children("require").map { parseRequire(it) }.toList(),
        removes = node.children("remove").map { parseRemove(it) }.toList(),
    )
}

private fun parseExtension(node: Node): Extension {
    val extensionNumber = node.attribute("number")?.toInt()
    return Extension(
        name = node.attribute("name") ?: error("Missing name attribute"),
        number = extensionNumber ?: error("Missing number attribute"),
        sortOrder = node.attribute("sortorder")?.toInt(),
        author = node.attribute("author"),
        contact = node.attribute("contact"),
        type = node.attribute("type"),
        depends = node.attribute("depends"),
        protect = node.attribute("protect"),
        platform = node.attribute("platform")?.split(",") ?: emptyList(),
        supported = node.attribute("supported")?.split(",") ?: emptyList(),
        ratified = node.attribute("ratified"),
        promotedTo = node.attribute("promotedto"),
        deprecatedBy = node.attribute("deprecatedby"),
        obsoleteBy = node.attribute("obsoleteby"),
        provisional = node.attribute("provisional") == "true",
        specialUse = node.attribute("specialuse"),
        requires = node.children("require").map { parseRequire(it, extensionNumber) }.toList(),
        removes = node.children("remove").map { parseRemove(it) }.toList(),
    )
}

private fun parseRequire(node: Node, extensionNumber: Int? = null): Require = Require(
    profile = node.attribute("profile"),
    api = node.attribute("api")?.split(",") ?: emptyList(),
    depends = node.attribute("depends"),
    commands = node.children("command").map { parseCommandReference(it) }.toList(),
    enumsReferences = node.children("enum").filter { isEnumReference(it) }.map { parseEnumReference(it) }
        .toList(),
    inlineEnums = node.children("enum").filter { isInlineEnum(it) }
        .map { parseInlineEnum(it, extensionNumber) }
        .toList(),
    types = node.children("type").map { parseTypeReference(it) }.toList(),
)

private fun isEnumReference(node: Node): Boolean = !isInlineEnum(node)

private fun isInlineEnum(node: Node): Boolean =
    listOf("value", "bitpos", "alias", "offset").any { node.hasAttribute(it) }

private fun parseCommandReference(node: Node): CommandReference = CommandReference(
    name = node.attribute("name") ?: error("Missing name attribute"),
)

private fun parseEnumReference(node: Node): EnumReference = EnumReference(
    name = node.attribute("name") ?: error("Missing name attribute"),
    api = node.attribute("api")?.split(",") ?: emptyList(),
)

private fun parseInlineEnum(node: Node, extensionNumber: Int?): InlineEnum = InlineEnum(
    name = node.attribute("name") ?: error("Missing name attribute"),
    type = node.attribute("type"),
    value = node.attribute("value"),
    bitPos = node.attribute("bitpos")?.toInt(),
    offset = node.attribute("offset")?.toInt(),
    dir = node.attribute("dir") == "-",
    extends = node.attribute("extends"),
    extensionNumber = node.attribute("extnumber")?.toInt() ?: extensionNumber,
    alias = node.attribute("alias"),
    protect = node.attribute("protect"),
)

private fun parseTypeReference(node: Node): TypeReference = TypeReference(
    name = node.attribute("name") ?: error("Missing name attribute"),
    api = node.attribute("api")?.split(",") ?: emptyList(),
)

private fun parseRemove(node: Node): Remove = Remove(
    profile = node.attribute("profile"),
    api = node.attribute("api")?.split(",") ?: emptyList(),
    commands = node.children("command").map { parseCommandReference(it) }.toList(),
    enumsReferences = node.children("enum").map { parseEnumReference(it) }.toList(),
    types = node.children("type").map { parseTypeReference(it) }.toList(),
)

