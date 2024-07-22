plugins {
    kotlin("jvm") version "1.9.24"
    idea
    `maven-publish`
    `java-library`

}

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
    api("org.lwjgl:lwjgl:3.3.4")
    api("org.lwjgl:lwjgl-vulkan:3.3.4")
    api("org.lwjgl:lwjgl-glfw:3.3.4")
    api("org.lwjgl:lwjgl-shaderc:3.3.4")

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

tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xcontext-receivers"
    }
}

kotlin {
    jvmToolchain(21)
}