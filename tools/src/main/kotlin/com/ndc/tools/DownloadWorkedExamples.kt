package com.ndc.tools

import com.ndc.tools.common.NdcConstants
import com.ndc.tools.common.extractMessageInfo
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.ZipFile

private const val BASE_URL = "https://standards.atlassian.net"
private const val WORKED_EXAMPLES_URL = "$BASE_URL/wiki/spaces/EASD/pages/574586985/Worked+Examples"

private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

private val httpClient: HttpClient = HttpClient.newBuilder()
    .followRedirects(HttpClient.Redirect.NORMAL)
    .connectTimeout(Duration.ofSeconds(30))
    .build()

private fun fetchPage(url: String): String {
    for (attempt in 1..3) {
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) return response.body()
            println("  Attempt $attempt: HTTP ${response.statusCode()}")
        } catch (e: Exception) {
            println("  Attempt $attempt failed: ${e.message}")
        }
        if (attempt < 3) Thread.sleep(2000)
    }
    return ""
}

private fun fetchBytes(url: String): ByteArray? {
    try {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", USER_AGENT)
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() in 200..299) return response.body()
    } catch (e: Exception) {
        println("    Error downloading: ${e.message}")
    }
    return null
}

data class ScenarioLink(val title: String, val url: String, val pageId: String)

fun getAllScenarioLinks(): List<ScenarioLink> {
    println("Fetching main Worked Examples page...")
    val content = fetchPage(WORKED_EXAMPLES_URL)
    if (content.isEmpty()) {
        println("Failed to fetch main page!")
        return emptyList()
    }

    val doc = Jsoup.parse(content, BASE_URL)
    val seen = mutableSetOf<String>()
    val scenarios = mutableListOf<ScenarioLink>()

    for (link in doc.select("a[href]")) {
        val href = link.attr("abs:href").ifEmpty { link.attr("href") }
        if ("/wiki/spaces/EASD/pages/" !in href || "Worked+Examples" in href) continue

        val title = link.text().trim()
        if (title.isEmpty() || "Worked Examples" in title) continue

        val pageIdMatch = Regex("""/pages/(\d+)/""").find(href) ?: continue
        val pageId = pageIdMatch.groupValues[1]

        if (pageId !in seen) {
            seen.add(pageId)
            val fullUrl = if (href.startsWith("http")) href else "$BASE_URL$href"
            scenarios.add(ScenarioLink(title, fullUrl, pageId))
        }
    }

    println("Found ${scenarios.size} scenario pages")
    return scenarios
}

private fun downloadAttachments(
    scenario: ScenarioLink,
    downloadsDir: File,
    sitePublicDir: File,
    metadata: MutableMap<String, MutableList<JsonObject>>,
    startCount: Int,
): Int {
    val folderName = NdcConstants.sanitizeFolderName(scenario.title)
    val folderPath = downloadsDir.resolve(folderName)
    folderPath.mkdirs()

    // Try bulk zip download first
    val zipUrl = "$BASE_URL/wiki/download/all_attachments?pageId=${scenario.pageId}"
    val zipBytes = fetchBytes(zipUrl)

    if (zipBytes != null && zipBytes.size > 100) {
        val zipPath = folderPath.resolve("attachments.zip")
        zipPath.writeBytes(zipBytes)

        try {
            val zipFile = ZipFile(zipPath)
            zipFile.use { zf ->
                for (entry in zf.entries()) {
                    if (entry.isDirectory) continue
                    val outFile = folderPath.resolve(entry.name)
                    outFile.parentFile.mkdirs()
                    zf.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            zipPath.delete()

            return processDownloadedFiles(folderPath, scenario, sitePublicDir, metadata, startCount)
        } catch (e: Exception) {
            println("    Warning: Not a valid zip, trying individual downloads")
            zipPath.delete()
        }
    }

    // Fallback: download attachments individually
    return downloadAttachmentsIndividually(scenario, folderPath, sitePublicDir, metadata, startCount)
}

private fun downloadAttachmentsIndividually(
    scenario: ScenarioLink,
    folderPath: File,
    sitePublicDir: File,
    metadata: MutableMap<String, MutableList<JsonObject>>,
    startCount: Int,
): Int {
    val content = fetchPage(scenario.url)
    if (content.isEmpty()) return 0

    val doc = Jsoup.parse(content, BASE_URL)
    var downloaded = 0
    var currentCount = startCount

    for (link in doc.select("a[href]")) {
        val href = link.attr("abs:href").ifEmpty { link.attr("href") }
        if ("/attachments/" !in href && "/download/" !in href) continue
        if (!href.contains(".xml")) continue

        val fullUrl = if (href.startsWith("http")) href else "$BASE_URL$href"
        var filename = link.text().trim()
        if (!filename.endsWith(".xml")) {
            filename = java.net.URLDecoder.decode(href.substringAfterLast("/").substringBefore("?"), "UTF-8")
        }

        if (filename.isNotEmpty()) {
            val fileBytes = fetchBytes(fullUrl) ?: continue
            val filePath = folderPath.resolve(NdcConstants.sanitizeFolderName(filename))
            filePath.writeBytes(fileBytes)

            val (msgName, version) = extractMessageInfo(filePath)
            if (msgName != null && version != null) {
                val targetFilename = "${msgName}_${version}_${currentCount}.xml"
                filePath.copyTo(sitePublicDir.resolve(targetFilename), overwrite = true)

                metadata.getOrPut(msgName) { mutableListOf() }.add(buildJsonObject {
                    put("title", scenario.title)
                    put("file", filePath.name)
                    put("url", scenario.url)
                    put("original_version", version)
                    put("path", "/worked_examples/$targetFilename")
                })
                currentCount++
                downloaded++
            }
        }
    }
    return downloaded
}

private fun processDownloadedFiles(
    folderPath: File,
    scenario: ScenarioLink,
    sitePublicDir: File,
    metadata: MutableMap<String, MutableList<JsonObject>>,
    startCount: Int,
): Int {
    var count = 0
    var currentCount = startCount

    for (xmlFile in folderPath.listFiles()?.filter { it.extension == "xml" } ?: emptyList()) {
        val (msgName, version) = extractMessageInfo(xmlFile)
        if (msgName == null || version == null) continue

        val targetFilename = "${msgName}_${version}_${currentCount}.xml"
        xmlFile.copyTo(sitePublicDir.resolve(targetFilename), overwrite = true)

        metadata.getOrPut(msgName) { mutableListOf() }.add(buildJsonObject {
            put("title", scenario.title)
            put("file", xmlFile.name)
            put("url", scenario.url)
            put("original_version", version)
            put("path", "/worked_examples/$targetFilename")
        })
        currentCount++
        count++
    }
    return count
}

fun main(args: Array<String>) {
    val sep = "=".repeat(60)
    println(sep)
    println("IATA NDC Worked Examples Sample Messages Downloader")
    println(sep)
    println()

    val projectRoot = NdcConstants.projectRoot()
    val downloadsDir = projectRoot.resolve("worked_examples_downloads")
    val siteDataDir = projectRoot.resolve("site/src/data")
    val sitePublicDir = projectRoot.resolve("site/public/worked_examples")
    val mappingFile = siteDataDir.resolve("worked_examples.json")

    downloadsDir.mkdirs()
    siteDataDir.mkdirs()
    if (sitePublicDir.exists()) sitePublicDir.deleteRecursively()
    sitePublicDir.mkdirs()

    val scenarios = getAllScenarioLinks()
    if (scenarios.isEmpty()) {
        println("No scenarios found!")
        return
    }

    println()
    println("Starting downloads...")
    println("-".repeat(60))

    var totalFiles = 0
    var successfulScenarios = 0
    val metadata = mutableMapOf<String, MutableList<JsonObject>>()

    for ((i, scenario) in scenarios.withIndex()) {
        println("\n[${i + 1}/${scenarios.size}] ${scenario.title}")
        println("  Page ID: ${scenario.pageId}")

        val filesSaved = downloadAttachments(scenario, downloadsDir, sitePublicDir, metadata, totalFiles)

        if (filesSaved > 0) {
            println("  \u2713 Processed $filesSaved file(s)")
            totalFiles += filesSaved
            successfulScenarios++
        } else {
            println("  \u26A0 No new xml files processed")
        }

        Thread.sleep(1000)
    }

    // Save metadata
    val jsonObj = buildJsonObject {
        for ((key, value) in metadata) {
            put(key, JsonArray(value))
        }
    }
    mappingFile.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), jsonObj))

    println()
    println(sep)
    println("Download Summary")
    println(sep)
    println("Total scenarios processed: ${scenarios.size}")
    println("Scenarios with downloads: $successfulScenarios")
    println("Total files processed: $totalFiles")
    println("Output directory: ${downloadsDir.absolutePath}")
    println("Public directory: ${sitePublicDir.absolutePath}")
    println("Metadata file: ${mappingFile.absolutePath}")
    println()
}
