package com.ndc.validator

import java.nio.file.Files
import kotlin.test.*

class ValidatorServiceTest {
    @Test
    fun acceptsValidXmlAndRejectsInvalidContent() {
        val root = Files.createTempDirectory("ndc-validator").toFile()
        try {
            val directory = root.resolve("fixture/TestMessage").apply { mkdirs() }
            directory.resolve("IATA_TestMessage.xsd").writeText("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="TestMessage">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="Count" type="xs:positiveInteger"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
            """.trimIndent())
            val validator = ValidatorService(SchemaService(root))
            val valid = validator.validate("fixture", "TestMessage", "<TestMessage><Count>1</Count></TestMessage>")
            assertTrue(valid.valid, valid.errors.joinToString())
            val invalid = validator.validate("fixture", "TestMessage", "<TestMessage><Count>invalid</Count></TestMessage>")
            assertFalse(invalid.valid)
            assertTrue(invalid.errors.isNotEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
