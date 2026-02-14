plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    application
}

kotlin {
    jvmToolchain(25)
}

group = "com.ndc"
version = "0.0.1"

application {
    mainClass.set("com.ndc.tools.ValidateWorkedExamplesKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jsoup:jsoup:1.18.3")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

// Named tasks for each tool
tasks.register<JavaExec>("validate") {
    description = "Validate worked examples against schemas"
    mainClass.set("com.ndc.tools.ValidateWorkedExamplesKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("download") {
    description = "Download IATA examples into canonical ndc_content structure"
    mainClass.set("com.ndc.tools.DownloadWorkedExamplesKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("buildContentCatalog") {
    description = "Build canonical examples catalog from iata/custom sources and validate flows"
    mainClass.set("com.ndc.tools.BuildContentCatalogKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("flatten") {
    description = "Flatten NDC schemas (all versions)"
    mainClass.set("com.ndc.tools.FlattenNdcSchemasKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("analyzeMissing") {
    description = "Analyze missing worked examples coverage"
    mainClass.set("com.ndc.tools.AnalyzeMissingExamplesKt")
    classpath = sourceSets["main"].runtimeClasspath
}
