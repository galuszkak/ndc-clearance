package com.ndc.validator

import java.io.File
import javax.xml.XMLConstants
import javax.xml.validation.SchemaFactory
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.*

/** Check every shipped release against the message registry, without release-specific assertions. */
@RunWith(Parameterized::class)
class SchemaCatalogTest(private val version: String, private val messages: List<String>) {
    companion object {
        private val root = File(System.getProperty("user.dir")).parentFile
        private val schemaRoot = root.resolve("ndc_schemas")
        private val schemas by lazy { SchemaService(schemaRoot) }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun releases(): List<Array<Any>> {
            val registered = Json.parseToJsonElement(root.resolve("iata_ndc_messages.json").readText())
                .jsonObject["versions"]!!.jsonObject
            val available = schemaRoot.listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.sorted()
            require(available.isNotEmpty()) { "No flattened schemas found in $schemaRoot" }
            require(available.all { it in registered }) { "Schema directories must be registered in iata_ndc_messages.json" }
            // Some registered releases have no schema sources/output. Test all shipped directories,
            // including empty or incomplete ones, rather than deriving cases from successful indexing.
            return available.map { version ->
                val messages = registered.getValue(version).jsonArray
                    .map { it.jsonPrimitive.content.removePrefix("IATA_") }.sorted()
                arrayOf<Any>(version, messages)
            }
        }
    }

    @Test
    fun discoversAndCompilesEveryRegisteredMessage() {
        assertEquals(messages, schemas.listSchemas()[version], "Message catalog mismatch for $version")
        val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).apply {
            setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file")
            setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        }
        for (message in messages) {
            val context = "$version/$message"
            val main = assertNotNull(schemas.getSchema(version, message), "Missing main schema: $context")
            val files = assertNotNull(schemas.getSchemaFiles(version, message), "Missing schema bundle: $context")
            assertTrue(main in files, "Main schema absent from bundle: $context")
            try {
                factory.newSchema(main)
            } catch (e: Exception) {
                throw AssertionError("Schema compilation failed for $context: ${e.message}", e)
            }
        }
    }
}
