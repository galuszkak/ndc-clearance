package com.ndc.tools

import com.ndc.tools.common.NdcConstants
import java.io.File
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.json.*

/** Import only registered NDC messages and their transitive XSD dependencies, preserving source bytes. */
fun importNdcSchemas(archive: File, destination: File, messages: List<String>) {
    require(messages.isNotEmpty()) { "No messages registered for this version" }
    ZipFile(archive).use { zip ->
        val entries = zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".xsd") }.toList()
        val byName = entries.groupBy { it.name.substringAfterLast('/') }
        val selected = linkedMapOf<String, ByteArray>()
        val pending = ArrayDeque(messages.map { "$it.xsd" })
        val builder = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }.newDocumentBuilder()
        while (pending.isNotEmpty()) {
            val name = pending.removeFirst()
            if (name in selected) continue
            // The supported IATA bundles use sibling XSD imports. Reject paths instead of flattening them silently.
            require(Regex("[A-Za-z0-9_.-]+\\.xsd").matches(name) && !name.startsWith(".")) {
                "Unsupported schema location: $name"
            }
            val matches = byName[name].orEmpty()
            require(matches.size == 1) { "Expected exactly one $name in $archive, found ${matches.size}" }
            val bytes = zip.getInputStream(matches.single()).use { it.readBytes() }
            val root = builder.parse(bytes.inputStream()).documentElement
            require(root.namespaceURI == XMLConstants.W3C_XML_SCHEMA_NS_URI && root.localName == "schema") {
                "$name is not an XML schema"
            }
            selected[name] = bytes
            for (tag in listOf("import", "include", "redefine")) {
                val refs = root.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, tag)
                for (i in 0 until refs.length) {
                    val location = refs.item(i).attributes.getNamedItem("schemaLocation")?.nodeValue
                    require(!location.isNullOrBlank()) { "Missing schemaLocation in $name" }
                    pending.add(location)
                }
            }
        }
        // Check the entire closure before writing. Reimporting identical sources is safe; never overwrite edits.
        for ((name, bytes) in selected) {
            val target = destination.resolve(name)
            require(!target.exists() || target.readBytes().contentEquals(bytes)) { "Conflicting existing source: $target" }
        }
        destination.mkdirs()
        for ((name, bytes) in selected) destination.resolve(name).writeBytes(bytes)
        println("Imported ${selected.size} source XSDs for ${messages.size} messages into $destination")
    }
}

fun main(args: Array<String>) {
    val options = args.associate {
        require(it.startsWith("--") && '=' in it) { "Use --archive=<zip> --version=<version>" }
        it.substringBefore('=') to it.substringAfter('=')
    }
    require(options.keys.all { it in setOf("--archive", "--version") }) { "Unknown import option" }
    val archive = File(requireNotNull(options["--archive"]) { "--archive is required" })
    val version = requireNotNull(options["--version"]) { "--version is required" }
    require(Regex("\\d+\\.\\d+(\\.\\d+)?").matches(version)) { "Invalid version: $version" }
    val root = NdcConstants.projectRoot()
    val versions = Json.parseToJsonElement(root.resolve("iata_ndc_messages.json").readText()).jsonObject["versions"]!!.jsonObject
    val messages = requireNotNull(versions[version]) { "Register $version in iata_ndc_messages.json first" }
        .jsonArray.map { it.jsonPrimitive.content }
    importNdcSchemas(archive, root.resolve("raw_ndc_schemas/${version}_ndc"), messages)
}
