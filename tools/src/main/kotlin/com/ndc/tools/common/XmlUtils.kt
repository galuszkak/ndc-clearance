package com.ndc.tools.common

import java.io.File

/**
 * Extract message name and version from an XML worked example file.
 * Reads the root element tag and xsi:schemaLocation attribute.
 */
fun extractMessageInfo(xmlFile: File): Pair<String?, String?> {
    try {
        val text = xmlFile.readText(Charsets.UTF_8)

        // Extract root element tag name (handles namespace prefixes)
        // Look for the first element after XML declaration
        val tagMatch = Regex("""<(?:\w+:)?(\w+)\s""").find(text, text.indexOf('<', text.indexOf("?>").coerceAtLeast(0)))
        val messageName = tagMatch?.groupValues?.get(1)

        // Extract version from schemaLocation
        var version: String? = null
        val versionMatch = Regex("""xsd/(\d+)/""").find(text)
        if (versionMatch != null) {
            val vId = versionMatch.groupValues[1]
            version = NdcConstants.VERSION_MAP[vId] ?: vId
        }

        return messageName to version
    } catch (e: Exception) {
        System.err.println("Error parsing ${xmlFile.name}: ${e.message}")
        return null to null
    }
}
