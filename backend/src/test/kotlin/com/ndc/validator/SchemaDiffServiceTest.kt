package com.ndc.validator

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class SchemaDiffServiceTest {

    @Test
    fun `test schema comparison`() {
        val tempRoot = File.createTempFile("schemas", "").parentFile.resolve("ndc_test_schemas_" + System.currentTimeMillis())
        tempRoot.mkdirs()
        
        try {
            // Setup Version 1
            val v1Dir = tempRoot.resolve("1.0/MsgA").also { it.mkdirs() }
            File(v1Dir, "MsgA.xsd").writeText("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified">
                    <xs:element name="MsgA">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="Field1" type="xs:string"/>
                                <xs:element name="Field2" type="xs:string">
                                    <xs:annotation><xs:documentation>Removed Doc</xs:documentation></xs:annotation>
                                </xs:element>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
            """.trimIndent())

            // Setup Version 2 (Field2 removed, Field3 added, Field1 doc changed)
            val v2Dir = tempRoot.resolve("2.0/MsgA").also { it.mkdirs() }
            File(v2Dir, "MsgA.xsd").writeText("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified">
                    <xs:element name="MsgA">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="Field1" type="xs:string">
                                    <xs:annotation><xs:documentation>New Doc</xs:documentation></xs:annotation>
                                </xs:element>
                                <xs:element name="Field3" type="xs:string">
                                    <xs:annotation><xs:documentation>Added Doc</xs:documentation></xs:annotation>
                                </xs:element>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
            """.trimIndent())

            val schemaService = SchemaService(tempRoot) // Uses our temp root
            val diffService = SchemaDiffService(schemaService)
            
            val diffs = diffService.compareVersions("1.0", "2.0")
            
            assertEquals(1, diffs.size)
            val msgDiff = diffs[0]
            assertEquals("MsgA", msgDiff.messageName)
            
            // Check finding Field2 Removed
            val removedItem = msgDiff.differences.find { it.path.endsWith("Field2") && it.type.name == "REMOVED" }
            kotlin.test.assertNotNull(removedItem, "Field2 should be removed")
            assertEquals("Removed Doc", removedItem.oldValue)
            
            // Check finding Field3 Added
            val addedItem = msgDiff.differences.find { it.path.endsWith("Field3") && it.type.name == "ADDED" }
            kotlin.test.assertNotNull(addedItem, "Field3 should be added")
            assertEquals("Added Doc", addedItem.newValue)
            
             // Check finding Field1 Doc Changed
            assertTrue(msgDiff.differences.any { it.path.endsWith("Field1") && it.type.name == "DOC_CHANGED" }, "Field1 doc should change")

        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
