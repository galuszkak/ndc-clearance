package com.ndc.tools

import com.ndc.tools.common.NdcConstants
import java.io.File
import java.nio.file.Files
import javax.xml.XMLConstants
import javax.xml.validation.SchemaFactory
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.*

/** Compile every available registered release before and after flattening, without modifying committed schemas. */
@RunWith(Parameterized::class)
class SchemaFlatteningTest(
    private val version: String,
    private val raw: File,
    private val messages: List<String>,
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun releases(): List<Array<Any>> {
            val root = NdcConstants.projectRoot()
            val versions = Json.parseToJsonElement(root.resolve("iata_ndc_messages.json").readText())
                .jsonObject["versions"]!!.jsonObject
            val rawRoot = root.resolve("raw_ndc_schemas")
            val rawFolders = rawRoot.listFiles().orEmpty().filter { it.isDirectory }.map { it.name }
            val cases = versions.entries.mapNotNull { (version, registered) ->
                // Match batch flattening, including raw folders with a patch suffix.
                val folder = findMatchingRawFolder(version, rawFolders) ?: return@mapNotNull null
                val messages = registered.jsonArray.map { it.jsonPrimitive.content }
                arrayOf<Any>(version, rawRoot.resolve(folder), messages)
            }
            require(cases.isNotEmpty()) { "No registered raw schema releases found in $rawRoot" }
            return cases
        }
    }

    @Test
    fun allRegisteredMessagesCompileBeforeAndAfterFlattening() {
        assertTrue(messages.isNotEmpty(), "No messages registered for $version")
        val output = Files.createTempDirectory("ndc-flatten-").toFile()
        try {
            val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).apply {
                setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file")
                setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            }
            val flattener = SchemaFlattener(raw)
            for (message in messages) {
                val filename = "$message.xsd"
                val source = raw.resolve(filename)
                assertTrue(source.isFile, "Missing raw schema: $version/$filename")
                compile(factory, source, "raw")
                val dir = output.resolve(message.removePrefix("IATA_")).apply { mkdirs() }
                val flattened = dir.resolve(filename)
                try {
                    flattener.flattenMessage(filename, flattened)
                } catch (e: Exception) {
                    throw AssertionError("Flattening failed for $version/$filename: ${e.message}", e)
                }
                compile(factory, flattened, "flattened")
            }
        } finally {
            output.deleteRecursively()
        }
    }

    private fun compile(factory: SchemaFactory, file: File, stage: String) {
        try {
            factory.newSchema(file)
        } catch (e: Exception) {
            throw AssertionError("Schema compilation failed for $version/${file.name} ($stage): ${e.message}", e)
        }
    }
}
