package com.stochastictinkr.vulkan.playground.generator

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinFileBuilderTest {
    @Test
    fun testWrite() {
        val baos = ByteArrayOutputStream()
        createKotlinFile({ PrintStream(baos) }) {
            packageName("com.stochastictinkr.vulkan.playground.generator")
            import("java.nio.file.Path")

            "fun test()" {
                `try` {
                    +"println(\"Hello, World!\")"
                }.catch("e: Exception") {
                    -"  // ignored"
                } finally {
                    +"println(\"Goodbye, World!\")"
                }
            }
        }

        val expected = """
            // Generated file.
            package com.stochastictinkr.vulkan.playground.generator
            
            import java.nio.file.Path
            
            fun test() {
                try {
                    println("Hello, World!")
                } catch (e: Exception) {
                      // ignored
                } finally {
                    println("Goodbye, World!")
                }
            }
        """.trimIndent() + "\n"

        assertEquals(expected, baos.toString())
    }
}