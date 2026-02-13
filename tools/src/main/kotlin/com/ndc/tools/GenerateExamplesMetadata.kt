package com.ndc.tools

import com.ndc.tools.common.NdcConstants
import com.ndc.tools.common.extractMessageInfo
import kotlinx.serialization.json.*
import java.io.File

/**
 * Simple fuzzy matching: returns the closest match from candidates if similarity >= cutoff.
 * Uses Levenshtein-like approach (longest common subsequence ratio).
 */
private fun closestMatch(query: String, candidates: Collection<String>, cutoff: Double = 0.6): String? {
    var best: String? = null
    var bestRatio = cutoff

    for (candidate in candidates) {
        val ratio = similarityRatio(query, candidate)
        if (ratio > bestRatio) {
            bestRatio = ratio
            best = candidate
        }
    }
    return best
}

private fun similarityRatio(a: String, b: String): Double {
    if (a.isEmpty() && b.isEmpty()) return 1.0
    val maxLen = maxOf(a.length, b.length)
    if (maxLen == 0) return 1.0
    val lcs = longestCommonSubsequence(a, b)
    return (2.0 * lcs) / (a.length + b.length)
}

private fun longestCommonSubsequence(a: String, b: String): Int {
    val m = a.length
    val n = b.length
    val dp = Array(m + 1) { IntArray(n + 1) }
    for (i in 1..m) {
        for (j in 1..n) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) {
                dp[i - 1][j - 1] + 1
            } else {
                maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }
    }
    return dp[m][n]
}

fun main(args: Array<String>) {
    val projectRoot = NdcConstants.projectRoot()
    val downloadsDir = projectRoot.resolve("worked_examples_downloads")
    val siteDataDir = projectRoot.resolve("site/src/data")
    val sitePublicDir = projectRoot.resolve("site/public/worked_examples")
    val mappingFile = siteDataDir.resolve("worked_examples.json")

    // Fetch current scenario links to map folder names → URLs
    val scenarios = getAllScenarioLinks()
    val scenariosMap = mutableMapOf<String, String>()
    for (s in scenarios) {
        val folderName = NdcConstants.sanitizeFolderName(s.title)
        scenariosMap[folderName] = s.url
    }

    siteDataDir.mkdirs()
    if (sitePublicDir.exists()) sitePublicDir.deleteRecursively()
    sitePublicDir.mkdirs()

    val metadata = mutableMapOf<String, MutableList<JsonObject>>()
    var count = 0

    for (scenarioDir in (downloadsDir.listFiles() ?: emptyArray()).filter { it.isDirectory }.sortedBy { it.name }) {
        val scenarioName = scenarioDir.name
        var url = scenariosMap[scenarioName] ?: ""

        if (url.isEmpty()) {
            println("WARNING: No URL found for scenario: '$scenarioName'")

            // Fuzzy match
            val fuzzyMatch = closestMatch(scenarioName, scenariosMap.keys)
            if (fuzzyMatch != null) {
                url = scenariosMap[fuzzyMatch]!!
                println("  -> Recovered via FUZZY match: '$fuzzyMatch' (URL found)")
            } else {
                // Prefix fallback
                val prefix = scenarioName.take(50)
                for ((sTitle, sUrl) in scenariosMap) {
                    if (sTitle.startsWith(prefix)) {
                        url = sUrl
                        println("  -> Recovered via PREFIX match: '$sTitle'")
                        break
                    }
                }
            }
        }

        for (xmlFile in scenarioDir.listFiles()?.filter { it.extension == "xml" } ?: emptyList()) {
            val (msgName, version) = extractMessageInfo(xmlFile)
            if (msgName == null || version == null) continue

            val targetFilename = "${msgName}_${version}_${count}.xml"
            xmlFile.copyTo(sitePublicDir.resolve(targetFilename), overwrite = true)

            metadata.getOrPut(msgName) { mutableListOf() }.add(buildJsonObject {
                put("title", scenarioName)
                put("file", xmlFile.name)
                put("url", url)
                put("original_version", version)
                put("path", "/worked_examples/$targetFilename")
            })
            count++
        }
    }

    val jsonObj = buildJsonObject {
        for ((key, value) in metadata) {
            put(key, JsonArray(value))
        }
    }
    mappingFile.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), jsonObj))

    println("Generated metadata for $count examples in $mappingFile")
}
