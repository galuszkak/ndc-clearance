package com.ndc.tools

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidateWorkedExamplesTest {

    private fun tempDir(): File = kotlin.io.path.createTempDirectory("ndc-test").toFile()

    private fun writeFile(dir: File, name: String, content: String): File {
        val file = dir.resolve(name)
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }

    @Test
    fun `extractSchemaInfo parses schemaLocation with version and filename`() {
        val dir = tempDir()
        val xml = writeFile(dir, "test.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <IATA_AirShoppingRQ xmlns="http://www.iata.org/IATA/2015/00/2019.2/IATA_AirShoppingRQ"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://www.iata.org/IATA/2015/00/2019.2/IATA_AirShoppingRQ https://airtechzone.iata.org/xsd/2134/IATA_AirShoppingRQ.xsd">
            </IATA_AirShoppingRQ>
        """.trimIndent())

        val (versionId, schemaFilename) = extractSchemaInfo(xml)
        assertEquals("2134", versionId)
        assertEquals("IATA_AirShoppingRQ.xsd", schemaFilename)
        dir.deleteRecursively()
    }

    @Test
    fun `extractSchemaInfo returns nulls when no schemaLocation`() {
        val dir = tempDir()
        val xml = writeFile(dir, "test.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://example.com">
            </Root>
        """.trimIndent())

        val (versionId, schemaFilename) = extractSchemaInfo(xml)
        assertNull(versionId)
        assertNull(schemaFilename)
        dir.deleteRecursively()
    }

    @Test
    fun `extractSchemaInfo returns nulls for malformed XML`() {
        val dir = tempDir()
        val xml = writeFile(dir, "test.xml", "not xml at all")

        val (versionId, schemaFilename) = extractSchemaInfo(xml)
        assertNull(versionId)
        assertNull(schemaFilename)
        dir.deleteRecursively()
    }

    @Test
    fun `validateXml returns valid for conforming XML`() {
        val dir = tempDir()
        val xsd = writeFile(dir, "test.xsd", """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="http://example.com/test"
                       xmlns:tns="http://example.com/test"
                       elementFormDefault="qualified">
                <xs:element name="Root">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="Name" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
        """.trimIndent())

        val xml = writeFile(dir, "test.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://example.com/test">
                <Name>Test</Name>
            </Root>
        """.trimIndent())

        val (isValid, error) = validateXml(xml, xsd)
        assertTrue(isValid, "Expected valid but got error: $error")
        assertEquals("", error)
        dir.deleteRecursively()
    }

    @Test
    fun `validateXml returns invalid with error for non-conforming XML`() {
        val dir = tempDir()
        val xsd = writeFile(dir, "test.xsd", """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="http://example.com/test"
                       xmlns:tns="http://example.com/test"
                       elementFormDefault="qualified">
                <xs:element name="Root">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="Name" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
        """.trimIndent())

        val xml = writeFile(dir, "test.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root xmlns="http://example.com/test">
                <WrongElement>Test</WrongElement>
            </Root>
        """.trimIndent())

        val (isValid, error) = validateXml(xml, xsd)
        assertFalse(isValid)
        assertTrue(error.isNotEmpty(), "Expected error message")
        dir.deleteRecursively()
    }

    @Test
    fun `checkWellFormed returns true for valid XML`() {
        val dir = tempDir()
        val xml = writeFile(dir, "test.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <Root><Child/></Root>
        """.trimIndent())

        val (wellFormed, _) = checkWellFormed(xml)
        assertTrue(wellFormed)
        dir.deleteRecursively()
    }

    @Test
    fun `checkWellFormed returns false for malformed XML`() {
        val dir = tempDir()
        val xml = writeFile(dir, "test.xml", "<Root><Unclosed>")

        val (wellFormed, error) = checkWellFormed(xml)
        assertFalse(wellFormed)
        assertTrue(error.isNotEmpty())
        dir.deleteRecursively()
    }

    @Test
    fun `resolveSchemaPath raw mode returns direct path`() {
        val schemasDir = File("/schemas")
        val path = resolveSchemaPath(SchemaType.RAW, schemasDir, "21.3.5_ndc", "IATA_AirShoppingRQ.xsd")
        assertEquals(File("/schemas/21.3.5_ndc/IATA_AirShoppingRQ.xsd"), path)
    }

    @Test
    fun `resolveSchemaPath flattened mode strips IATA_ prefix for folder`() {
        val schemasDir = File("/schemas")
        val path = resolveSchemaPath(SchemaType.FLATTENED, schemasDir, "21.3.5", "IATA_AirShoppingRQ.xsd")
        assertEquals(File("/schemas/21.3.5/AirShoppingRQ/IATA_AirShoppingRQ.xsd"), path)
    }

    @Test
    fun `resolveSchemaPath flattened mode handles non-IATA prefix`() {
        val schemasDir = File("/schemas")
        val path = resolveSchemaPath(SchemaType.FLATTENED, schemasDir, "21.3.5", "SomeSchema.xsd")
        assertEquals(File("/schemas/21.3.5/SomeSchema/SomeSchema.xsd"), path)
    }
}
