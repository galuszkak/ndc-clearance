package com.ndc.tools

import com.ndc.tools.common.NdcConstants
import kotlinx.serialization.json.*
import java.io.File

private fun getAllMessageTypes(jsonPath: File): Set<String> {
    val data = Json.parseToJsonElement(jsonPath.readText()).jsonObject
    val versions = data["versions"]?.jsonObject ?: return emptySet()

    val unique = mutableSetOf<String>()
    for ((_, messages) in versions) {
        for (msg in messages.jsonArray) {
            unique.add(msg.jsonPrimitive.content.replace("IATA_", ""))
        }
    }
    return unique
}

private fun getMessageTypeFromXml(file: File): String? {
    try {
        val text = file.readText(Charsets.UTF_8)
        // Find the root element tag name after XML declaration
        val startIdx = text.indexOf("?>").let { if (it >= 0) it + 2 else 0 }
        val tagMatch = Regex("""<(?:\w+:)?(\w+)[\s>]""").find(text, startIdx) ?: return null
        var tag = tagMatch.groupValues[1]
        if (tag.startsWith("IATA_")) tag = tag.removePrefix("IATA_")
        return tag
    } catch (_: Exception) {
        return null
    }
}

private fun getExistingExamplesFromXml(examplesDir: File): Pair<Set<String>, Int> {
    val found = mutableSetOf<String>()
    var filesChecked = 0

    examplesDir.walk().filter { it.isFile && it.extension == "xml" }.forEach { file ->
        filesChecked++
        val msgType = getMessageTypeFromXml(file)
        if (msgType != null) found.add(msgType)
    }

    return found to filesChecked
}

fun main(args: Array<String>) {
    val projectRoot = NdcConstants.projectRoot()
    val jsonPath = projectRoot.resolve("iata_ndc_messages.json")
    val examplesDir = projectRoot.resolve("worked_examples_downloads")

    val allMsgs = getAllMessageTypes(jsonPath)
    val (foundMsgs, fileCount) = getExistingExamplesFromXml(examplesDir)

    val missing = (allMsgs - foundMsgs).sorted()

    println("Total defined message types (normalized): ${allMsgs.size}")
    println("XML files scanned: $fileCount")
    println("Message types found in examples: ${foundMsgs.size}")
    println("Missing message types: ${missing.size}")
    println()
    println("Missing Message Types:")
    for (m in missing) {
        println("- $m")
    }
}
