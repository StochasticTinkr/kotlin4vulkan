package com.stochastictinkr.vulkan.playground.generator

import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A class for writing Kotlin files. This class is used to generate Kotlin code in a structured way.
 */
class KotlinFileBuilder(
    packageName: CharSequence?,
    private val imports: Imports,
    private val lines: MutableList<CharSequence>,
    private val indent: String = "",
) : Imports by imports {
    var packageName: CharSequence? = packageName
        private set
    private var deferred: KotlinWritable? = null

    /**
     * Defer writing until the end of the current block.
     */
    private fun defer(deferred: KotlinWritable) {
        flushDeferred()
        this.deferred = deferred
    }

    /**
     * Clear the deferred write after writing it.
     */
    fun flushDeferred() {
        deferred?.run {
            deferred = null
            write()
        }
    }

    /**
     * Set the package name for the file. This should be called at most once.
     */
    fun packageName(packageName: CharSequence) {
        if (this.packageName != null) error("Package already set to ${this.packageName}")
        this.packageName = packageName
    }


    private fun addLine(line: String) {
        flushDeferred()
        lines += line
    }

    /**
     * Add a line to the file, replacing the indent with the current indent.
     */
    operator fun CharSequence.unaryPlus() {
        addLine(this@CharSequence.toString().replaceIndent(indent))
    }

    /**
     * Add a line to the file if the boolean is true.
     */
    operator fun Boolean.times(line: String) {
        if (this) +line
    }

    /**
     * Add a line to the file, prepending the current indent.
     */
    operator fun CharSequence.unaryMinus() {
        addLine("$indent$this")
    }

    /**
     * Add lines to the file, replacing the indent with the current indent.
     */
    operator fun Iterable<CharSequence>.unaryPlus() = forEach { +it }

    /**
     * Add lines to the file, prepending the current indent.
     */
    operator fun Iterable<CharSequence>.unaryMinus() = forEach { -it }

    /**
     * Add lines to the file, replacing the indent with the current indent.
     */
    operator fun Sequence<CharSequence>.unaryPlus() = forEach { +it }

    /**
     * Add lines to the file, prepending the current indent.
     */
    operator fun Sequence<CharSequence>.unaryMinus() = forEach { -it }

    /**
     * Write a KotlinWritable to the file
     */
    operator fun KotlinWritable?.unaryPlus() = this?.run { write() }

    @JvmName("writeKotlinWritables")
    /**
     * Write a collection of KotlinWritable to the file.
     */
    operator fun Iterable<KotlinWritable>.unaryPlus() = forEach { +it }

    @JvmName("writeKotlinWritables")
    /**
     * Write a sequence of KotlinWritable to the file.
     */
    operator fun Sequence<KotlinWritable>.unaryPlus() = forEach { +it }

    /**
     * Create a try-catch block to write to the file.
     */
    fun `try`(tryBlock: KotlinFileBuilder.() -> Unit) = TryBuilder(tryBlock).also {
        defer(it)
    }

    /**
     * If unwrapped is false, write a wrapped block to the file. Otherwise, write the block as is
     *
     * example for wrapped:
     * ```
     *   "fun foo()" {
     *      +"println(\"Hello, World!\")"
     *   }
     * ```
     * will write:
     * ```
     * fun foo() {
     *    println("Hello, World!")
     * }
     * ```
     *
     * example for unwrapped:
     * ```
     *  val alwaysCheck = true
     *  "if (check)"(unwrapped = alwaysCheck) {
     *    +"doCheck()"
     *  }
     * ```
     * will write:
     * ```
     * doCheck()
     * ```
     *
     * If skipIf is true, the block will not be written.
     *
     * @param useNamedParams Unused: Forces the use of named parameters.
     * @param unwrapped If true, the block will be written without wrapping braces and opening line.
     * @param skipIf If true, nothing will be written.
     */
    inline operator fun CharSequence.invoke(
        @Suppress("UNUSED_PARAMETER") useNamedParams: UseNamedParams = UseNamedParams,
        skipIf: Boolean = false,
        unwrapped: Boolean = false,
        lambdaParams: String? = null,
        keepHeadlineIndent: Boolean = false,
        block: KotlinFileBuilder.() -> Unit,
    ) {
        when {
            skipIf -> return
            unwrapped -> block()
            else -> {
                val headline = "$this {${lambdaParams?.let { " $it ->" } ?: ""}"
                if (keepHeadlineIndent) -headline else +headline
                indent { block() }
                +"}"
            }
        }
    }

    operator fun CharSequence.invoke(
        @Suppress("UNUSED_PARAMETER") useNamedParams: UseNamedParams = UseNamedParams,
        skipIf: Boolean = false,
        keepIndent: Boolean = false,
    ) {
        if (skipIf) return
        if (keepIndent) -this else +this
    }

    /**
     * Write a block of code with the given number of spaces of indentation.
     */
    @OptIn(ExperimentalContracts::class)
    inline fun indent(spaces: Int = 4, block: KotlinFileBuilder.() -> Unit) {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        indented(spaces).run {
            block()
            flushDeferred()
        }
    }

    inline operator fun <T> Iterable<T>.rangeTo(block: (T) -> CharSequence) {
        forEach { +block(it) }
    }

    inline operator fun <T> Sequence<T>.rangeTo(block: (T) -> CharSequence) {
        forEach { +block(it) }
    }

    fun indented(spaces: Int = 4) =
        KotlinFileBuilder(packageName, imports, lines, "$indent${" ".repeat(spaces)}")
}

@OptIn(ExperimentalContracts::class)
inline fun <R> createKotlinFile(noinline openStream: () -> PrintStream, block: KotlinFileBuilder.() -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    val imports = mutableSetOf<String>()
    val lines = mutableListOf<CharSequence>()
    val writer = KotlinFileBuilder(null, ImportsImpl(imports), lines)
    return try {
        block(writer).also {
            writer.flushDeferred()
        }
    } finally {
        writeKotlinFile(writer.packageName, imports, lines, openStream)
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <R> createKotlinFile(path: Path, block: KotlinFileBuilder.() -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return createKotlinFile({ openStream(path) }, block)
}

@OptIn(ExperimentalContracts::class)
fun createKotlinFile(root: Path, `package`: String, fileName: String, block: KotlinFileBuilder.() -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    val file = if (!fileName.endsWith(".kt")) "$fileName.kt" else fileName
    val path = `package`.split('.').fold(root) { acc, part -> acc.resolve(part) }.resolve(file)
    createKotlinFile(path) {
        this.packageName(`package`)
        block()
    }
}

private val ignoredImports: Set<String> = setOf(
    "java.lang.CharSequence",
    "java.lang.String",
    "int",
    "long",
    "float",
    "double",
    "boolean",
)

fun writeKotlinFile(
    `package`: CharSequence?,
    imports: Iterable<Any?>,
    lines: Iterable<Any?>,
    openStream: () -> PrintStream,
) {
    `package` ?: error("Package not set")
    openStream().use { out ->
        out.println("// Generated file.")
        out.println("package $`package`")

        var needsBlank = true
        imports.map { it.toString() }
            .sorted()
            .filterNot { it in ignoredImports }
            .forEach {
                if (needsBlank) {
                    out.println()
                    needsBlank = false
                }
                out.println("import $it")
            }

        needsBlank = true
        lines.forEach {
            if (needsBlank) {
                out.println()
                needsBlank = false
            }
            out.println(it)
        }
    }
}

fun openStream(path: Path): PrintStream {
    path.parent.toFile().mkdirs()
    check(path.parent.toFile().isDirectory) { "Unable to create directory ${path.parent}" }
    return PrintStream(FileOutputStream(path.toFile()).buffered(), false)
}

interface KotlinWritable {
    fun KotlinFileBuilder.write()
}

class TryBuilder(val tryBlock: KotlinFileBuilder.() -> Unit) : KotlinWritable {
    private val catches = mutableListOf<Pair<String, KotlinFileBuilder.() -> Unit>>()
    private var finally: (KotlinFileBuilder.() -> Unit)? = null

    override fun KotlinFileBuilder.write() {
        if (catches.isEmpty() && finally == null) error("try block must have at least one catch or finally clause!")
        -"try {"
        indent {
            tryBlock()
        }
        catches.forEach { (exception, block) ->
            -"} catch (${exception}) {"
            indent {
                block()
            }
        }
        finally?.let {
            -"} finally {"
            indent {
                it()
            }
        }
        -"}"
    }

    /**
     * Add a `catch` block to the try-catch block.
     */
    fun catch(exception: String, block: KotlinFileBuilder.() -> Unit): TryBuilder {
        catches += exception to block
        return this
    }

    /**
     * Add a `finally` block to the try-catch-finally.
     */
    infix fun finally(block: KotlinFileBuilder.() -> Unit) {
        if (finally != null) error("finally block already set!")
        this.finally = block
    }
}

