package com.ndc.tools

import com.ndc.tools.common.NdcConstants
import kotlinx.serialization.json.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.StringWriter
import java.util.ArrayDeque
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

private const val XSD_NS = "http://www.w3.org/2001/XMLSchema"

internal data class DefinitionKey(val ns: String, val name: String)

internal data class SchemaInfo(
    val doc: Document,
    val root: Element,
    val targetNs: String,
    val prefixMap: Map<String, String>, // prefix → URI
    val definitions: Map<DefinitionKey, Element>,
)

class SchemaFlattener(private val sourceDir: File) {
    private val schemas = mutableMapOf<File, SchemaInfo>() // absolute path → info
    private val globalDefinitions = mutableMapOf<DefinitionKey, Element>()
    private val externalSchemas = mutableMapOf<String, String>() // namespace → schemaLocation

    internal fun loadSchema(filename: String): SchemaInfo? {
        val path = sourceDir.resolve(filename).canonicalFile
        schemas[path]?.let { return it }

        val doc: Document
        try {
            val dbf = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
            doc = dbf.newDocumentBuilder().parse(path)
        } catch (e: Exception) {
            println("Error loading $path: ${e.message}")
            return null
        }

        val root = doc.documentElement
        val targetNs = root.getAttribute("targetNamespace") ?: ""

        // Extract prefix→URI map from the root element's attributes
        val prefixMap = mutableMapOf<String, String>()
        val attrs = root.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            if (attr.nodeName.startsWith("xmlns:")) {
                prefixMap[attr.nodeName.removePrefix("xmlns:")] = attr.nodeValue
            } else if (attr.nodeName == "xmlns") {
                prefixMap[""] = attr.nodeValue
            }
        }

        // Collect global definitions
        val defs = mutableMapOf<DefinitionKey, Element>()
        val defTags = setOf("complexType", "simpleType", "element", "group", "attributeGroup")
        var child = root.firstChild
        while (child != null) {
            if (child is Element && child.localName in defTags) {
                val name = child.getAttribute("name")
                if (name.isNotEmpty()) {
                    val key = DefinitionKey(targetNs, name)
                    defs[key] = child
                }
            }
            child = child.nextSibling
        }

        val info = SchemaInfo(doc, root, targetNs, prefixMap, defs)
        schemas[path] = info
        globalDefinitions.putAll(defs)

        // Handle imports/includes recursively
        child = root.firstChild
        while (child != null) {
            if (child is Element && child.localName in listOf("import", "include")) {
                val loc = child.getAttribute("schemaLocation")
                val ns = child.getAttribute("namespace")
                if (loc.isNotEmpty()) {
                    if (ns.isNotEmpty() && !ns.startsWith("http://www.iata.org")) {
                        externalSchemas.putIfAbsent(ns, loc)
                    } else {
                        loadSchema(loc)
                    }
                }
            }
            child = child.nextSibling
        }

        return info
    }

    private fun resolveReference(value: String, currentNs: String, prefixMap: Map<String, String>): DefinitionKey? {
        if (value.isEmpty()) return null
        val colonIdx = value.indexOf(':')
        if (colonIdx >= 0) {
            val prefix = value.substring(0, colonIdx)
            val localName = value.substring(colonIdx + 1)
            val ns = prefixMap[prefix]
            if (ns != null) return DefinitionKey(ns, localName)
            if (prefix == "xs") return DefinitionKey(XSD_NS, localName)
            return DefinitionKey("", localName)
        }
        return DefinitionKey(currentNs, value)
    }

    internal fun findUsedDefinitions(mainPath: File): Set<DefinitionKey> {
        val mainSchema = schemas[mainPath] ?: return emptySet()
        val usedKeys = mutableSetOf<DefinitionKey>()
        val queue = ArrayDeque<DefinitionKey>()

        // Seed with all global definitions in the main schema
        for (key in mainSchema.definitions.keys) {
            if (key.ns == mainSchema.targetNs) {
                queue.add(key)
                usedKeys.add(key)
            }
        }

        val refAttrs = listOf("type", "base", "ref", "itemType", "substitutionGroup")

        while (queue.isNotEmpty()) {
            val currentKey = queue.poll()
            val element = globalDefinitions[currentKey] ?: continue

            // Find origin schema for prefix resolution
            val originSchema = schemas.values.firstOrNull {
                it.definitions[currentKey] === element
            } ?: continue

            // Scan element and all descendants for references
            fun checkRef(value: String) {
                val refKey = resolveReference(value, originSchema.targetNs, originSchema.prefixMap) ?: return
                if (refKey.ns == XSD_NS) return
                if (refKey in globalDefinitions && refKey !in usedKeys) {
                    usedKeys.add(refKey)
                    queue.add(refKey)
                }
            }

            forEachElement(element) { el ->
                for (attr in refAttrs) {
                    val v = el.getAttribute(attr)
                    if (v.isNotEmpty()) checkRef(v)
                }
            }
        }

        return usedKeys
    }

    fun flattenMessage(messageFilename: String, outputPath: File) {
        val mainInfo = loadSchema(messageFilename) ?: return
        val mainPath = sourceDir.resolve(messageFilename).canonicalFile

        val usedKeys = findUsedDefinitions(mainPath)
        val mainNs = mainInfo.targetNs

        // Separate definitions by namespace
        val defsByNs = mutableMapOf<String, MutableList<DefinitionKey>>()
        for (key in usedKeys) {
            defsByNs.getOrPut(key.ns) { mutableListOf() }.add(key)
        }

        val otherNamespaces = defsByNs.keys.filter { it != mainNs && it != XSD_NS }

        // Build prefix map for common types namespaces
        val cnsNamespaces = mutableMapOf<String, String>() // namespace → prefix
        for (ns in otherNamespaces) {
            val foundPrefix = schemas.values.firstNotNullOfOrNull { schema ->
                schema.prefixMap.entries.firstOrNull { it.value == ns && it.key.isNotEmpty() }?.key
            }
            val prefix = foundPrefix ?: "cns${cnsNamespaces.size}"
            cnsNamespaces[ns] = prefix
        }

        // Scan for used external namespaces
        val usedExternalNs = mutableSetOf<String>()
        val refAttrs = listOf("type", "base", "ref", "itemType", "substitutionGroup")

        for (key in usedKeys) {
            val element = globalDefinitions[key] ?: continue
            val originSchema = schemas.values.firstOrNull { it.definitions[key] === element } ?: continue

            forEachElement(element) { el ->
                for (attr in refAttrs) {
                    val v = el.getAttribute(attr)
                    if (v.isNotEmpty()) {
                        val refKey = resolveReference(v, originSchema.targetNs, originSchema.prefixMap)
                        if (refKey != null && refKey.ns in externalSchemas) {
                            usedExternalNs.add(refKey.ns)
                        }
                    }
                }
            }
        }

        // Collect external namespace prefixes
        val externalNsPrefixes = mutableMapOf<String, String>()
        for (schema in schemas.values) {
            for ((pfx, uri) in schema.prefixMap) {
                if (uri in usedExternalNs && uri !in cnsNamespaces && uri != mainNs && uri != XSD_NS && pfx.isNotEmpty()) {
                    externalNsPrefixes.putIfAbsent(uri, pfx)
                }
            }
        }

        val baseName = outputPath.nameWithoutExtension
        val outputDir = outputPath.parentFile

        fun getCTSuffix(ns: String) = if ("FullyOptional" in ns) "_OptionalCommonTypes.xsd" else "_CommonTypes.xsd"

        // Helper: rewrite prefixes in a cloned element
        fun rewritePrefixes(elem: Element, originSchema: SchemaInfo, isCnsSchema: Boolean) {
            forEachElement(elem) { el ->
                for (attr in refAttrs) {
                    val v = el.getAttribute(attr)
                    if (v.isEmpty()) continue
                    val refKey = resolveReference(v, originSchema.targetNs, originSchema.prefixMap) ?: continue

                    val newValue = when {
                        refKey.ns == XSD_NS -> "xs:${refKey.name}"
                        refKey.ns in cnsNamespaces -> "${cnsNamespaces[refKey.ns]}:${refKey.name}"
                        refKey.ns == mainNs -> if (isCnsSchema) "msg:${refKey.name}" else refKey.name
                        refKey.ns in externalNsPrefixes -> "${externalNsPrefixes[refKey.ns]}:${refKey.name}"
                        else -> refKey.name
                    }
                    el.setAttribute(attr, newValue)
                }
            }
        }

        val outDoc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().newDocument()

        // === Generate CommonTypes files ===
        for ((cnsTargetNs, cnsTargetPrefix) in cnsNamespaces) {
            if (cnsTargetNs !in defsByNs) continue

            val ctDoc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().newDocument()
            val ctRoot = ctDoc.createElementNS(XSD_NS, "xs:schema")
            ctRoot.setAttribute("targetNamespace", cnsTargetNs)
            ctRoot.setAttribute("elementFormDefault", "qualified")
            ctRoot.setAttribute("version", "1.0")
            ctRoot.setAttribute("xmlns:xs", XSD_NS)
            ctRoot.setAttribute("xmlns:$cnsTargetPrefix", cnsTargetNs)
            ctDoc.appendChild(ctRoot)

            val usedExtNs = mutableSetOf<String>()
            val usedOtherCns = mutableSetOf<String>()

            val sortedKeys = defsByNs[cnsTargetNs]!!.sortedBy { it.name }
            for (key in sortedKeys) {
                val original = globalDefinitions[key] ?: continue
                val originSchema = schemas.values.firstOrNull { it.definitions[key] === original } ?: continue

                // Check for external and other cns references
                forEachElement(original) { el ->
                    for (attr in refAttrs) {
                        val v = el.getAttribute(attr)
                        if (v.isEmpty()) continue
                        val rk = resolveReference(v, originSchema.targetNs, originSchema.prefixMap) ?: continue
                        when {
                            rk.ns in externalNsPrefixes -> usedExtNs.add(rk.ns)
                            rk.ns in cnsNamespaces && rk.ns != cnsTargetNs -> usedOtherCns.add(rk.ns)
                        }
                    }
                }

                val cloned = ctDoc.importNode(original, true) as Element
                rewritePrefixes(cloned, originSchema, isCnsSchema = true)
                ctRoot.appendChild(cloned)
            }

            // Add imports for external namespaces
            for (extNs in usedExtNs.sorted()) {
                val importEl = ctDoc.createElementNS(XSD_NS, "xs:import")
                importEl.setAttribute("namespace", extNs)
                // Find schemaLocation from original imports
                for (schema in schemas.values) {
                    var imp = schema.root.firstChild
                    while (imp != null) {
                        if (imp is Element && imp.localName == "import" && imp.getAttribute("namespace") == extNs) {
                            val sl = imp.getAttribute("schemaLocation")
                            if (sl.isNotEmpty()) importEl.setAttribute("schemaLocation", sl)
                        }
                        imp = imp.nextSibling
                    }
                }
                ctRoot.insertBefore(importEl, ctRoot.firstChild)
                ctRoot.setAttribute("xmlns:${externalNsPrefixes[extNs]}", extNs)
            }

            // Add imports for other common types namespaces
            for (otherCnsNs in usedOtherCns.sorted()) {
                val importEl = ctDoc.createElementNS(XSD_NS, "xs:import")
                importEl.setAttribute("namespace", otherCnsNs)
                importEl.setAttribute("schemaLocation", "$baseName${getCTSuffix(otherCnsNs)}")
                ctRoot.insertBefore(importEl, ctRoot.firstChild)
                ctRoot.setAttribute("xmlns:${cnsNamespaces[otherCnsNs]}", otherCnsNs)
            }

            val ctOutputPath = outputDir.resolve("$baseName${getCTSuffix(cnsTargetNs)}")
            writeSchemaFile(ctDoc, ctOutputPath)
        }

        // === Generate Main Message file ===
        val mainDoc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().newDocument()
        val mainRoot = mainDoc.createElementNS(XSD_NS, "xs:schema")
        mainRoot.setAttribute("targetNamespace", mainNs)
        mainRoot.setAttribute("elementFormDefault", "qualified")
        mainRoot.setAttribute("version", mainInfo.root.getAttribute("version").ifEmpty { "1.0" })
        mainRoot.setAttribute("xmlns:xs", XSD_NS)
        mainRoot.setAttribute("xmlns", mainNs)
        mainDoc.appendChild(mainRoot)

        // Add xmlns declarations for all common types prefixes
        for ((ns, prefix) in cnsNamespaces) {
            if (prefix.isNotEmpty()) {
                mainRoot.setAttribute("xmlns:$prefix", ns)
            }
        }

        // Add imports for common types
        for ((ns, _) in cnsNamespaces) {
            if (ns in defsByNs) {
                val importEl = mainDoc.createElementNS(XSD_NS, "xs:import")
                importEl.setAttribute("namespace", ns)
                importEl.setAttribute("schemaLocation", "$baseName${getCTSuffix(ns)}")
                mainRoot.appendChild(importEl)
            }
        }

        // Add main namespace definitions
        if (mainNs in defsByNs) {
            val sortedKeys = defsByNs[mainNs]!!.sortedBy { it.name }
            for (key in sortedKeys) {
                val original = globalDefinitions[key] ?: continue
                val originSchema = schemas.values.firstOrNull { it.definitions[key] === original } ?: continue

                val cloned = mainDoc.importNode(original, true) as Element
                rewritePrefixes(cloned, originSchema, isCnsSchema = false)
                mainRoot.appendChild(cloned)
            }
        }

        writeSchemaFile(mainDoc, outputPath)

        // Copy external schema files
        for (ns in usedExternalNs) {
            val loc = externalSchemas[ns] ?: continue
            val src = sourceDir.resolve(loc)
            val dst = outputDir.resolve(loc)
            if (src.exists() && !dst.exists()) {
                src.copyTo(dst)
                println("Copied external schema: $loc")
            }
        }
    }

    /** Clear state for processing a new message (keeps loaded schemas for reuse) */
    fun reset() {
        // Keep schemas loaded but clear global defs and externals
        // Actually, for batch processing of different versions, we need full reset
        schemas.clear()
        globalDefinitions.clear()
        externalSchemas.clear()
    }
}

private fun forEachElement(node: Node, action: (Element) -> Unit) {
    if (node is Element) action(node)
    var child = node.firstChild
    while (child != null) {
        forEachElement(child, action)
        child = child.nextSibling
    }
}

private fun writeSchemaFile(doc: Document, outputPath: File) {
    val transformer = TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "yes")
        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        setOutputProperty(OutputKeys.ENCODING, "UTF-8")
    }

    val writer = StringWriter()
    transformer.transform(DOMSource(doc), StreamResult(writer))

    // Clean up: remove empty lines
    val result = writer.toString()
        .lines()
        .filter { it.isNotBlank() }
        .joinToString("\n")

    outputPath.writeText(result)
    println("Generated $outputPath")
}

// === Batch flatten (replaces batch_flatten.py) ===

private fun findMatchingRawFolder(version: String, rawFolders: List<String>): String? {
    return rawFolders.firstOrNull { folder ->
        folder == version || folder.startsWith("$version.") || folder.startsWith("${version}_")
    }
}

fun main(args: Array<String>) {
    // Support both single-version mode and batch mode
    var inputDir: String? = null
    var outputDir: String? = null
    var messageList: String? = null
    var version: String? = null

    for (arg in args) {
        when {
            arg.startsWith("--input-dir=") -> inputDir = arg.removePrefix("--input-dir=")
            arg.startsWith("--output-dir=") -> outputDir = arg.removePrefix("--output-dir=")
            arg.startsWith("--message-list=") -> messageList = arg.removePrefix("--message-list=")
            arg.startsWith("--version=") -> version = arg.removePrefix("--version=")
        }
    }

    if (inputDir != null && outputDir != null) {
        // Single-version mode (like flatten_ndc_schemas.py)
        flattenSingleVersion(File(inputDir), File(outputDir), messageList, version)
    } else {
        // Batch mode (like batch_flatten.py)
        batchFlatten()
    }
}

private fun flattenSingleVersion(inputDir: File, outputDir: File, messageListArg: String?, versionArg: String?) {
    outputDir.mkdirs()

    val targetFiles = mutableListOf<String>()
    if (messageListArg != null) {
        if (messageListArg.endsWith(".json")) {
            val data = Json.parseToJsonElement(File(messageListArg).readText()).jsonObject
            if (versionArg != null && "versions" in data) {
                val versions = data["versions"]!!.jsonObject
                if (versionArg in versions) {
                    targetFiles.addAll(versions[versionArg]!!.jsonArray.map { it.jsonPrimitive.content + ".xsd" })
                } else {
                    println("Version not found or invalid JSON structure")
                    return
                }
            }
        } else {
            targetFiles.addAll(messageListArg.split(",").map {
                val s = it.trim()
                if (s.endsWith(".xsd")) s else "$s.xsd"
            })
        }
    }

    if (targetFiles.isEmpty()) {
        println("No target files found.")
        return
    }

    val flattener = SchemaFlattener(inputDir)

    for (fname in targetFiles) {
        if (!inputDir.resolve(fname).exists()) {
            println("Skipping $fname (not found)")
            continue
        }

        val msgName = fname.removeSuffix(".xsd")
        val folderName = if (msgName.startsWith("IATA_")) msgName.removePrefix("IATA_") else msgName

        val msgOutputDir = outputDir.resolve(folderName)
        msgOutputDir.mkdirs()

        flattener.flattenMessage(fname, msgOutputDir.resolve(fname))
    }
}

private fun batchFlatten() {
    val projectRoot = NdcConstants.projectRoot()
    val jsonPath = projectRoot.resolve("iata_ndc_messages.json")
    val rawDir = projectRoot.resolve("raw_ndc_schemas")
    val outputBaseDir = projectRoot.resolve("ndc_schemas")

    if (!jsonPath.exists()) {
        println("Error: $jsonPath not found.")
        return
    }

    val data = Json.parseToJsonElement(jsonPath.readText()).jsonObject
    val versions = data["versions"]?.jsonObject?.keys ?: return

    val rawFolders = rawDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()

    for (version in versions) {
        val match = findMatchingRawFolder(version, rawFolders)
        if (match == null) {
            println("Skipping version $version: No matching directory found in $rawDir")
            continue
        }

        val inputDir = rawDir.resolve(match)
        val outputDir = outputBaseDir.resolve(version)

        println("--- Processing version $version (from $match) ---")

        flattenSingleVersion(inputDir, outputDir, jsonPath.absolutePath, version)
        println("Successfully flattened schemas for version $version into $outputDir")
    }
}
