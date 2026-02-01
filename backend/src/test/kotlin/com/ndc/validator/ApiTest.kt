package com.ndc.validator

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class ApiTest {

    private val schemaService = SchemaService()
    private val validatorService = ValidatorService(schemaService)

    @Test
    fun validateAllExamples() {
        // Locate worked_examples_downloads directory
        // Assuming the test runs from backend/ directory or we can find it relative to project root
        val projectRoot = File(System.getProperty("user.dir")).parentFile
        val examplesDir = File(projectRoot, "worked_examples_downloads")

        if (!examplesDir.exists()) {
            println("Skipping test: worked_examples_downloads not found at ${examplesDir.absolutePath}")
            return
        }

        val failures = mutableListOf<String>()
        var testedCount = 0

        examplesDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
            .forEach { file ->
                println("Testing file: ${file.name}")
                try {
                    val xmlContent = file.readText()
                    // Extract message name from root element
                    val messageName = extractMessageName(xmlContent)
                    
                    if (messageName != null) {
                        // Validate against all known versions for this message
                        // Since we don't know which version the example intends, 
                        // or if the request was to run against ALL versions, we'll try to find any schema that matches this message.
                        
                        val schemas = schemaService.listSchemas()
                        // schemas is Map<Version, List<MessageName>>
                        
                        var validatedAtLeastOnce = false
                        
                        schemas.forEach { (version, messages) ->
                            if (messages.contains(messageName)) {
                                val result = validatorService.validate(version, messageName, xmlContent)
                                testedCount++
                                if (!result.valid) {
                                    // It's possible an example is valid for one version but not another.
                                    // However, the requirement was "run those working examples against all versions of APIs"
                                    // If strict compliance is expected for ALL versions, we record failure.
                                    // If we just want to see IF it validates against *some* version, that's different.
                                    // Given "working examples", they likely should be valid.
                                    // But schema evolution might break older/newer validation?
                                    // Let's record failures but maybe we need to be more lenient or specific?
                                    // For now, let's just log them and fail if it doesn't validate against ANY version? 
                                    // OR fail if it fails against specific target version?
                                    // The prompt said: "run those working examples against all versions of API".
                                    // Implication: They should pass? Or we just want to know results?
                                    // "I need to have tests of API... I would expect to run those working examples against all versions of APIs"
                                    // Usually this implies regression testing.
                                    // Let's assume strictness for now, but maybe with a soft assertion style.
                                    
                                    failures.add("File: ${file.name}, Version: $version, Message: $messageName, Errors: ${result.errors.joinToString(", ")}")
                                }
                                validatedAtLeastOnce = true
                            }
                        }
                        
                        if (!validatedAtLeastOnce) {
                             // No schema found for this message in any version
                             println("WARNING: No schema found for message '$messageName' in any version. (File: ${file.name})")
                        }

                    } else {
                        failures.add("Could not extract message name from ${file.name}")
                    }
                } catch (e: Exception) {
                    failures.add("Exception processing ${file.name}: ${e.message}")
                }
            }

        println("Tested $testedCount combinations.")
        
        if (failures.isNotEmpty()) {
            fail("Validation failed for the following:\n" + failures.take(20).joinToString("\n") + (if (failures.size > 20) "\n... and ${failures.size - 20} more" else ""))
        }
    }

    private fun extractMessageName(xml: String): String? {
        // Simple regex or substring to find root element name
        // <easd:IATA_OrderChangeNotifRQ ...> -> IATA_OrderChangeNotifRQ
        // or <IATA_OrderChangeNotifRQ ...>
        // We need to handle namespaces.
        
        // Match <(prefix:)?(MessageName)
        val regex = Regex("<([a-zA-Z0-9_]+:)?(IATA_[a-zA-Z0-9_]+)")
        val match = regex.find(xml)
        if (match != null) {
            return match.groupValues[2] // The message name part
        }
        
        // Fallback for non-IATA_ prefixed root elements if they exist?
        // The schema service implies message names match XSD names.
        // Let's try to match just the local name of root element.
        val fallbackRegex = Regex("<([a-zA-Z0-9_]+:)?([a-zA-Z0-9_]+)[ >]")
        val fallbackMatch = fallbackRegex.find(xml)
        // Skip <?xml ...?>
        if (fallbackMatch != null) {
             val candidate = fallbackMatch.groupValues[2]
             if (candidate != "xml") return candidate
        }

        return null
    }
}
