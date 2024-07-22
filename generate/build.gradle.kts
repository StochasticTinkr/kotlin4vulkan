import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.HttpURLConnection
import java.net.URI

plugins {
    kotlin("jvm")
    application
}

group = "com.stochastictinkr"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    implementation("org.lwjgl:lwjgl:3.3.4")
    runtimeOnly("org.lwjgl:lwjgl-vulkan:3.3.4")

    // Command line parsing:
    implementation("com.xenomachina:kotlin-argparser:2.0.7")
    implementation("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("io.mockk:mockk:1.13.12")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xcontext-receivers"
    }
}

val vulkanDocsVersion = "1.3.288"
tasks.register("downloadVulkanDocs") {
    group = "vulkan-docs"
    val targetFile = layout.buildDirectory.file("vulkan-docs.zip").get().asFile
    outputs.file(targetFile)
    doFirst {
        if (targetFile.exists()) {
            throw StopExecutionException("Not overwriting $targetFile")
        }
        // Download "https://github.com/KhronosGroup/Vulkan-Docs/archive/refs/heads/main.zip"
        targetFile.parentFile.mkdirs()

        (URI("https://github.com/KhronosGroup/Vulkan-Docs/archive/refs/tags/v$vulkanDocsVersion.zip")
            .toURL()
            .openConnection() as HttpURLConnection)
            .let { connection ->
                connection.connect()
                when (connection.responseCode) {
                    200 -> {
                        println("Downloading Vulkan docs: ${connection.contentLengthLong} bytes to $targetFile")
                        targetFile.outputStream().use { output ->
                            connection.inputStream.use { input -> input.copyTo(output) }
                        }
                    }
                    else -> error("Failed to download Vulkan docs: ${connection.responseMessage}")
                }
            }

    }
}

tasks.register<Copy>("extractVulkanDocs") {
    group = "vulkan-docs"
    dependsOn("downloadVulkanDocs")
    from(zipTree(file("build/vulkan-docs.zip")))
    into(file("build/vulkan-docs"))
}

tasks.register<JavaExec>("generateClasses") {
    group = "vulkan-docs"
    dependsOn("extractVulkanDocs", "classes")
    val inputDir = layout.buildDirectory.dir("vulkan-docs/Vulkan-Docs-$vulkanDocsVersion")
    inputs.dir(inputDir)
    val outputDir = rootProject.layout.buildDirectory.dir("generated-sources/src/main/kotlin")
    outputs.dir(outputDir)
    mainClass = "com.stochastictinkr.vulkan.playground.generator.GenerateKt"
    classpath = sourceSets.main.get().runtimeClasspath
    args(
        "--clean",
        "--input", inputDir.get().asFile.absolutePath,
        "--output", outputDir.get().asFile.absolutePath
    )
}

kotlin {
    jvmToolchain(21)
}

