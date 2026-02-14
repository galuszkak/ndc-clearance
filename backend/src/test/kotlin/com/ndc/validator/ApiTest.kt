package com.ndc.validator

import java.io.File
import kotlin.test.Test
import kotlin.test.fail
import kotlinx.serialization.json.Json

class ApiTest {

    private val schemaService = SchemaService()
    private val validatorService = ValidatorService(schemaService)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun validateIataExamplesAgainstDeclaredSchemas() {
        val projectRoot = File(System.getProperty("user.dir")).parentFile
        val contentRoot = File(projectRoot, "ndc_content")
        val catalogFile = File(contentRoot, "examples/catalog.json")

        if (!catalogFile.exists()) {
            println("Skipping test: catalog not found at ${catalogFile.absolutePath}")
            return
        }

        val catalog = json.decodeFromString(ExampleCatalog.serializer(), catalogFile.readText())
        val examples = catalog.examples.filter { it.source == "iata" && it.isActive }

        if (examples.isEmpty()) {
            println("Skipping test: no active IATA examples in catalog")
            return
        }

        val hardFailures = mutableListOf<String>()
        val softFailures = mutableListOf<String>()
        var testedCount = 0
        var validCount = 0

        for (example in examples) {
            val xmlFile = resolveXmlFile(contentRoot, example.xmlPath)
            if (!xmlFile.exists()) {
                hardFailures.add("Missing XML for ${example.id}: ${xmlFile.absolutePath}")
                continue
            }

            val xmlContent = xmlFile.readText()
            val candidates = listOf(example.message.removePrefix("IATA_"), example.message).distinct()
            var finalResult: ValidationResult? = null
            var selectedMessage = candidates.first()

            for (candidate in candidates) {
                val result = validatorService.validate(example.version, candidate, xmlContent)
                if (finalResult == null) {
                    finalResult = result
                    selectedMessage = candidate
                }

                val schemaNotFound = result.errors.any { it.contains("Schema not found", ignoreCase = true) }
                if (!schemaNotFound) {
                    finalResult = result
                    selectedMessage = candidate
                    break
                }
            }

            testedCount++
            if (finalResult == null) {
                hardFailures.add("No validation result for ${example.id}")
                continue
            }

            val schemaNotFound = finalResult.errors.any { it.contains("Schema not found", ignoreCase = true) }
            if (schemaNotFound) {
                hardFailures.add(
                    "Schema not found for ${example.id} (version=${example.version}, message=$selectedMessage)",
                )
            } else if (finalResult.valid) {
                validCount++
            } else {
                val errors = finalResult.errors.joinToString(", ")
                softFailures.add(
                    "Example ${example.id} invalid (version=${example.version}, message=$selectedMessage): $errors",
                )
            }
        }

        println("Tested $testedCount IATA examples from ndc_content.")
        println("Valid examples: $validCount")
        if (softFailures.isNotEmpty()) {
            println("Non-blocking invalid examples: ${softFailures.size}")
            softFailures.take(5).forEach { println("  $it") }
        }

        if (validCount == 0) {
            fail("No IATA examples validated successfully from ndc_content.")
        }

        if (hardFailures.isNotEmpty()) {
            fail(
                "Hard failures while validating canonical examples:\n" +
                    hardFailures.take(20).joinToString("\n") +
                    if (hardFailures.size > 20) "\n... and ${hardFailures.size - 20} more" else "",
            )
        }
    }

    private fun resolveXmlFile(contentRoot: File, xmlPath: String): File {
        val normalized = xmlPath.replace('\\', '/')
        val relative = if (normalized.startsWith("ndc_content/")) {
            normalized.removePrefix("ndc_content/")
        } else {
            normalized
        }
        return contentRoot.resolve(relative)
    }
}
