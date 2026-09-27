package com.ndc.tools

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class ImportNdcSchemasTest {
    private fun withArchive(entries: Map<String, String>, block: (File, File) -> Unit) {
        val root = Files.createTempDirectory("ndc-import").toFile()
        try {
            val archive = root.resolve("release.zip")
            ZipOutputStream(archive.outputStream()).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
            block(archive, root.resolve("sources"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun schema(body: String = "") = """<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">$body</xs:schema>"""

    @Test
    fun importsTransitiveDependenciesAndPreservesBytes() {
        val main = schema("""<xs:include schemaLocation="Common.xsd"/>""")
        val common = schema("""<xs:include schemaLocation="Signature.xsd"/>""")
        withArchive(mapOf("Release/IATA_Test.xsd" to main, "Release/Common.xsd" to common,
            "Release/Signature.xsd" to schema(), "Release/Unrelated.xsd" to schema())) { zip, out ->
            importNdcSchemas(zip, out, listOf("IATA_Test"))
            assertEquals(setOf("IATA_Test.xsd", "Common.xsd", "Signature.xsd"), out.list()!!.toSet())
            assertEquals(main, out.resolve("IATA_Test.xsd").readText())
            importNdcSchemas(zip, out, listOf("IATA_Test"))
            out.resolve("Common.xsd").writeText("local edit")
            assertFailsWith<IllegalArgumentException> { importNdcSchemas(zip, out, listOf("IATA_Test")) }
            assertEquals("local edit", out.resolve("Common.xsd").readText())
        }
    }

    @Test
    fun rejectsMissingDependenciesBeforeWriting() {
        withArchive(mapOf("IATA_Test.xsd" to schema("""<xs:include schemaLocation="Missing.xsd"/>"""))) { zip, out ->
            assertFailsWith<IllegalArgumentException> { importNdcSchemas(zip, out, listOf("IATA_Test")) }
            assertFalse(out.exists())
        }
    }

    @Test
    fun rejectsAmbiguousEntriesAndUnsafeImports() {
        withArchive(mapOf("a/IATA_Test.xsd" to schema(), "b/IATA_Test.xsd" to schema())) { zip, out ->
            assertFailsWith<IllegalArgumentException> { importNdcSchemas(zip, out, listOf("IATA_Test")) }
        }
        for (location in listOf("../Outside.xsd", "/Outside.xsd", "https://example.com/Outside.xsd")) {
            withArchive(mapOf("IATA_Test.xsd" to schema("""<xs:include schemaLocation="$location"/>"""))) { zip, out ->
                assertFailsWith<IllegalArgumentException> { importNdcSchemas(zip, out, listOf("IATA_Test")) }
                assertFalse(out.exists())
            }
        }
    }
}
