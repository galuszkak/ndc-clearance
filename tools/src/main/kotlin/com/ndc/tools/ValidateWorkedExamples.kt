package com.ndc.tools

import com.ndc.tools.common.NdcConstants
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import kotlin.system.exitProcess

enum class SchemaType { RAW, FLATTENED }

data class ValidationEntry(
    val scenario: String,
    val file: String,
    val version: String,
    val schemaFolder: String,
    val status: String,
    val error: String,
)

data class VersionStats(
    var total: Int = 0,
    var valid: Int = 0,
    var invalid: Int = 0,
    var noSchema: Int = 0,
)

/**
 * Extract schema version ID and schema filename from an XML file's xsi:schemaLocation attribute.
 * Looks for pattern: xsd/{versionId}/{schemaFilename}.xsd
 * Uses text-based regex to handle partially malformed XML (matching Python's iterparse behavior).
 */
fun extractSchemaInfo(xmlFile: File): Pair<String?, String?> {
    try {
        // Read file as text and use regex — this works even on malformed XML
        // where the root element's attributes are valid but the body is broken.
        // Matches the behavior of Python's lxml.iterparse (which reads start events
        // before encountering errors deeper in the document).
        val text = xmlFile.readText(Charsets.UTF_8)
        val match = Regex("""xsd/(\d+)/(\w+\.xsd)""").find(text)
        if (match != null) {
            return match.groupValues[1] to match.groupValues[2]
        }
    } catch (e: Exception) {
        System.err.println("  Error reading ${xmlFile.name}: ${e.message}")
    }
    return null to null
}

/**
 * Validate an XML file against an XSD schema file.
 * Returns (isValid, errorMessage).
 */
fun validateXml(xmlFile: File, schemaFile: File): Pair<Boolean, String> {
    try {
        val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        val schema = factory.newSchema(schemaFile)
        val validator = schema.newValidator()

        val errors = mutableListOf<String>()
        validator.errorHandler = object : ErrorHandler {
            override fun warning(e: SAXParseException) {}
            override fun error(e: SAXParseException) {
                errors.add("Line ${e.lineNumber}, Col ${e.columnNumber}: ${e.message}")
            }
            override fun fatalError(e: SAXParseException) {
                errors.add("Line ${e.lineNumber}, Col ${e.columnNumber}: ${e.message}")
            }
        }
        validator.validate(StreamSource(xmlFile))

        return if (errors.isEmpty()) {
            true to ""
        } else {
            false to errors.first()
        }
    } catch (e: Exception) {
        return false to "Validation exception: ${e.message}"
    }
}

/**
 * Check if an XML file is well-formed by attempting to parse it.
 */
fun checkWellFormed(xmlFile: File): Pair<Boolean, String> {
    try {
        val dbf = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
        }
        val builder = dbf.newDocumentBuilder()
        // Suppress default [Fatal Error] output to stderr
        builder.setErrorHandler(object : org.xml.sax.ErrorHandler {
            override fun warning(e: SAXParseException) {}
            override fun error(e: SAXParseException) {}
            override fun fatalError(e: SAXParseException) { throw e }
        })
        builder.parse(xmlFile)
        return true to ""
    } catch (e: Exception) {
        val msg = e.message ?: e.toString()
        return false to msg.take(200)
    }
}

/**
 * Resolve the schema file path based on schema type (raw or flattened).
 */
fun resolveSchemaPath(
    schemaType: SchemaType,
    schemasDir: File,
    schemaFolder: String,
    schemaFilename: String,
): File {
    return when (schemaType) {
        SchemaType.RAW -> schemasDir.resolve(schemaFolder).resolve(schemaFilename)
        SchemaType.FLATTENED -> {
            val msgName = schemaFilename.removeSuffix(".xsd")
            val folderName = if (msgName.startsWith("IATA_")) msgName.removePrefix("IATA_") else msgName
            schemasDir.resolve(schemaFolder).resolve(folderName).resolve(schemaFilename)
        }
    }
}

fun main(args: Array<String>) {
    // Parse arguments
    var schemaType = SchemaType.FLATTENED
    var examplesDir: File? = null
    var schemasDir: File? = null

    for (arg in args) {
        when {
            arg.startsWith("--schema-type=") -> {
                val value = arg.removePrefix("--schema-type=")
                schemaType = when (value.lowercase()) {
                    "raw" -> SchemaType.RAW
                    "flattened" -> SchemaType.FLATTENED
                    else -> {
                        System.err.println("Unknown schema type: $value (use 'raw' or 'flattened')")
                        exitProcess(1)
                    }
                }
            }
            arg.startsWith("--examples-dir=") -> examplesDir = File(arg.removePrefix("--examples-dir="))
            arg.startsWith("--schemas-dir=") -> schemasDir = File(arg.removePrefix("--schemas-dir="))
        }
    }

    val projectRoot = NdcConstants.projectRoot()
    val effectiveExamplesDir = examplesDir ?: projectRoot.resolve("worked_examples_downloads")
    val effectiveSchemasDir = schemasDir ?: when (schemaType) {
        SchemaType.RAW -> projectRoot.resolve("raw_ndc_schemas")
        SchemaType.FLATTENED -> projectRoot.resolve("ndc_schemas")
    }

    val versionMap = when (schemaType) {
        SchemaType.RAW -> NdcConstants.RAW_VERSION_MAP
        SchemaType.FLATTENED -> NdcConstants.FLATTENED_VERSION_MAP
    }

    val schemaLabel = when (schemaType) {
        SchemaType.RAW -> "raw"
        SchemaType.FLATTENED -> "FLATTENED"
    }

    // Header
    val sep = "=".repeat(100)
    val dash = "-".repeat(100)

    println(sep)
    if (schemaType == SchemaType.FLATTENED) {
        println("IATA NDC Worked Examples - Schema Validation Report (using FLATTENED schemas)")
    } else {
        println("IATA NDC Worked Examples - Schema Validation Report")
    }
    println(sep)
    println()

    // Check directories
    if (!effectiveExamplesDir.exists()) {
        println("Error: $effectiveExamplesDir not found. Run download script first.")
        exitProcess(1)
    }
    if (!effectiveSchemasDir.exists()) {
        println("Error: $effectiveSchemasDir not found.")
        exitProcess(1)
    }

    // Collect all XML files (sorted)
    val xmlFiles = effectiveExamplesDir.walk()
        .filter { it.isFile && it.extension == "xml" }
        .sortedBy { it.absolutePath }
        .toList()

    println("Found ${xmlFiles.size} XML files to validate")
    if (schemaType == SchemaType.FLATTENED) {
        println("Using flattened schemas from: $effectiveSchemasDir")
    }
    println()

    // Results
    val results = mutableListOf<ValidationEntry>()
    val versionStats = mutableMapOf<String, VersionStats>()

    fun stats(key: String) = versionStats.getOrPut(key) { VersionStats() }

    for (xmlFile in xmlFiles) {
        val scenario = xmlFile.parentFile.name
        val filename = xmlFile.name

        // Well-formedness check for flattened mode
        if (schemaType == SchemaType.FLATTENED) {
            val (wellFormed, wellFormedError) = checkWellFormed(xmlFile)
            if (!wellFormed) {
                results.add(ValidationEntry(scenario, filename, "N/A", "N/A", "MALFORMED_XML", wellFormedError))
                stats("malformed").total++
                stats("malformed").invalid++
                continue
            }
        }

        // Extract schema info
        val (versionId, schemaFilename) = extractSchemaInfo(xmlFile)

        if (versionId == null || schemaFilename == null) {
            results.add(ValidationEntry(scenario, filename, "N/A", "N/A", "NO_SCHEMA_REF",
                "Could not extract schema reference from XML"))
            stats("unknown").total++
            stats("unknown").noSchema++
            continue
        }

        // Map version to schema folder
        val schemaFolder = versionMap[versionId]
        if (schemaFolder == null) {
            results.add(ValidationEntry(scenario, filename, versionId, "UNMAPPED", "UNMAPPED_VERSION",
                "Version $versionId not in VERSION_MAP"))
            stats(versionId).total++
            stats(versionId).noSchema++
            continue
        }

        // Find schema file
        val schemaPath = resolveSchemaPath(schemaType, effectiveSchemasDir, schemaFolder, schemaFilename)
        if (!schemaPath.exists()) {
            results.add(ValidationEntry(scenario, filename, versionId, schemaFolder, "SCHEMA_NOT_FOUND",
                "Schema file not found: $schemaPath"))
            stats(versionId).total++
            stats(versionId).noSchema++
            continue
        }

        // Validate
        val (isValid, errorMsg) = validateXml(xmlFile, schemaPath)

        stats(versionId).total++
        if (isValid) {
            stats(versionId).valid++
            results.add(ValidationEntry(scenario, filename, versionId, schemaFolder, "VALID", ""))
        } else {
            stats(versionId).invalid++
            results.add(ValidationEntry(scenario, filename, versionId, schemaFolder, "INVALID", errorMsg))
        }
    }

    // Summary by version
    println(sep)
    println("SUMMARY BY SCHEMA VERSION")
    println(sep)
    println("%-12s %-20s %-8s %-8s %-8s %-10s".format("Version ID", "Schema Folder", "Total", "Valid", "Invalid", "No Schema"))
    println(dash)

    val unmappedLabel = when (schemaType) {
        SchemaType.RAW -> "UNMAPPED"
        SchemaType.FLATTENED -> "N/A"
    }

    for (vid in versionStats.keys.sorted()) {
        val s = versionStats[vid]!!
        val folder = versionMap[vid] ?: unmappedLabel
        println("%-12s %-20s %-8d %-8d %-8d %-10d".format(vid, folder, s.total, s.valid, s.invalid, s.noSchema))
    }

    val totalFiles = versionStats.values.sumOf { it.total }
    val totalValid = versionStats.values.sumOf { it.valid }
    val totalInvalid = versionStats.values.sumOf { it.invalid }
    val totalNoSchema = versionStats.values.sumOf { it.noSchema }

    println(dash)
    println("%-12s %-20s %-8d %-8d %-8d %-10d".format("TOTAL", "", totalFiles, totalValid, totalInvalid, totalNoSchema))
    println()

    // Validation failures
    val failedResults = results.filter { it.status != "VALID" }

    if (failedResults.isNotEmpty()) {
        println(sep)
        println("VALIDATION FAILURES")
        println(sep)
        println()

        val byScenario = failedResults.groupBy { it.scenario }
        for (scenario in byScenario.keys.sorted()) {
            println("\uD83D\uDCC1 $scenario") // 📁
            for (r in byScenario[scenario]!!) {
                println("   \u274C ${r.file}") // ❌
                println("      Version: ${r.version} -> ${r.schemaFolder}")
                println("      Status: ${r.status}")
                if (r.error.isNotEmpty()) {
                    val error = if (r.error.length > 200) r.error.take(200) + "..." else r.error
                    println("      Error: $error")
                }
                println()
            }
        }
    } else {
        println("\u2705 All files validated successfully!") // ✅
    }

    // Full validation table
    println()
    println(sep)
    println("FULL VALIDATION TABLE")
    println(sep)
    println("%-12s %-8s %-15s %-55s".format("Status", "Version", "Schema", "File"))
    println(dash)

    for (r in results) {
        val icon = if (r.status == "VALID") "\u2705" else "\u274C" // ✅ or ❌
        val fileDisplay = if (r.file.length > 50) r.file.take(50) + "..." else r.file
        println("$icon %-10s %-8s %-15s %s".format(r.status, r.version, r.schemaFolder, fileDisplay))
    }

    println()
    println(sep)
    println("Total: $totalFiles files | Valid: $totalValid | Invalid: $totalInvalid | No Schema: $totalNoSchema")
    println(sep)

    exitProcess(if (totalInvalid == 0) 0 else 1)
}
