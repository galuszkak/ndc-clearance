plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("io.ktor.plugin") version "3.6.0"

}

kotlin {
    jvmToolchain(25)
}

group = "com.ndc"
version = "0.0.1"

application {
    mainClass.set("com.ndc.validator.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")
    implementation("io.ktor:ktor-server-sse-jvm")
    implementation("io.modelcontextprotocol:kotlin-sdk:0.15.0")
    implementation("com.posthog:posthog-server:2.17.0")


    testImplementation("io.ktor:ktor-server-test-host:3.6.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

tasks.register<JavaExec>("precomputeDiffs") {
    group = "build"
    description = "Precompute schema diff JSON for all version pairs into ../ndc_diffs"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ndc.validator.PrecomputeDiffsKt")
    args = listOf("../ndc_schemas", "../ndc_diffs")
}
