import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.lwjgl.Lwjgl
import org.lwjgl.lwjgl

plugins {
    kotlin("jvm") version "1.9.24"
    id("org.lwjgl.plugin") version "0.0.34"
    idea
    `maven-publish`
    `java-library`

}
data class Version(override val string: String) : org.lwjgl.Version

group = "com.stochastictinkr"
version = "1.0-SNAPSHOT"

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

publishing {
    publications {
        create<MavenPublication>(project.name) {
            from(components["java"])
        }
    }
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    lwjgl {
        version = Version("3.3.4")
        implementation(
            Lwjgl.Module.core,
            Lwjgl.Module.vulkan,
        )
    }
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}

tasks.compileKotlin {
    dependsOn(":generate:generateClasses")
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(":generate:generateClasses")
}

sourceSets {
    main {
        kotlin {
            srcDir(layout.buildDirectory.dir("generated-sources/src/main/kotlin"))
        }
    }
}


tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xcontext-receivers"
    }
}

kotlin {
    jvmToolchain(21)
}