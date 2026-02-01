package com.ndc.validator

import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

import kotlinx.serialization.Serializable

@Serializable
data class ValidationResult(val valid: Boolean, val errors: List<String>)

class ValidatorService(private val schemaService: SchemaService) {
    private val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)

    fun validate(version: String, message: String, xmlContent: String): ValidationResult {
        val schemaFile = schemaService.getSchema(version, message)
            ?: return ValidationResult(false, listOf("Schema not found for version \$version and message \$message"))

        val errors = mutableListOf<String>()
        
        try {
            // Create schema from the specific XSD file
            // Since our schemas are flattened and self-contained (or correctly relative),
            // Xerces should resolve imports relative to this file.
            val schema = schemaFactory.newSchema(schemaFile)
            val validator = schema.newValidator()
            
            validator.errorHandler = object : ErrorHandler {
                override fun warning(exception: SAXParseException) {
                    // Warnings don't fail validation but are good to note?
                    // For now ignore
                }

                override fun error(exception: SAXParseException) {
                    errors.add(formatError(exception))
                }

                override fun fatalError(exception: SAXParseException) {
                    errors.add(formatError(exception))
                }
            }

            validator.validate(StreamSource(StringReader(xmlContent)))

        } catch (e: Exception) {
            errors.add("Validation exception: \${e.message}")
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    private fun formatError(e: SAXParseException): String {
        return "Line ${e.lineNumber}, Col ${e.columnNumber}: ${e.message}"
    }
}
