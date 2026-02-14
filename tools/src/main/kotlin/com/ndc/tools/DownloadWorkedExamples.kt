package com.ndc.tools

import com.ndc.tools.common.ExampleSourceRecord
import com.ndc.tools.common.ExampleSourcesFile
import com.ndc.tools.common.NdcConstants
import com.ndc.tools.common.buildExampleId
import com.ndc.tools.common.contentJson
import com.ndc.tools.common.ensureCanonicalXmlPath
import com.ndc.tools.common.extractMessageInfo
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.ZipFile
import kotlinx.serialization.encodeToString
import kotlin.io.path.createTempDirectory
import org.jsoup.Jsoup

private const val BASE_URL = "https://standards.atlassian.net"
private const val WORKED_EXAMPLES_URL = "$BASE_URL/wiki/spaces/EASD/pages/574586985/Worked+Examples"

private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

private val httpClient: HttpClient = HttpClient.newBuilder()
    .followRedirects(HttpClient.Redirect.NORMAL)
    .connectTimeout(Duration.ofSeconds(30))
    .build()

data class ScenarioLink(val title: String, val url: String, val pageId: String)

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

private fun saveExample(
    xmlFile: File,
    scenario: ScenarioLink,
    examplesDir: File,
    records: MutableMap<String, ExampleSourceRecord>,
): Boolean {
    val (msgName, version) = extractMessageInfo(xmlFile)
    if (msgName == null || version == null) return false

    val id = buildExampleId(
        source = "iata",
        message = msgName,
        version = version,
        sourcePageId = scenario.pageId,
        sourceUrl = scenario.url,
        fileName = xmlFile.name,
    )

    val targetFile = examplesDir.resolve("$id.xml")
    xmlFile.copyTo(targetFile, overwrite = true)

    val record = ExampleSourceRecord(
        id = id,
        source = "iata",
        message = msgName,
        version = version,
        title = scenario.title,
        fileName = xmlFile.name,
        xmlPath = ensureCanonicalXmlPath("ndc_content/examples/files/iata/${targetFile.name}"),
        sourceUrl = scenario.url,
        sourcePageId = scenario.pageId,
        isActive = true,
    )

    records[id] = record
    return true
}

private fun downloadAttachments(
    scenario: ScenarioLink,
    examplesDir: File,
    records: MutableMap<String, ExampleSourceRecord>,
): Int {
    val tempDir = createTempDirectory("ndc-iata-${scenario.pageId}-").toFile()
    try {
        val zipUrl = "$BASE_URL/wiki/download/all_attachments?pageId=${scenario.pageId}"
        val zipBytes = fetchBytes(zipUrl)

        if (zipBytes != null && zipBytes.size > 100) {
            val zipPath = tempDir.resolve("attachments.zip")
            zipPath.writeBytes(zipBytes)

            try {
                ZipFile(zipPath).use { zf ->
                    for (entry in zf.entries()) {
                        if (entry.isDirectory) continue
                        val outFile = tempDir.resolve(entry.name)
                        outFile.parentFile.mkdirs()
                        zf.getInputStream(entry).use { input ->
                            outFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
                zipPath.delete()
                val fromZip = processDownloadedFiles(tempDir, scenario, examplesDir, records)
                if (fromZip > 0) return fromZip
            } catch (_: Exception) {
                println("    Warning: Not a valid zip, trying individual downloads")
                zipPath.delete()
            }
        }

        return downloadAttachmentsIndividually(scenario, tempDir, examplesDir, records)
    } finally {
        tempDir.deleteRecursively()
    }
}

private fun downloadAttachmentsIndividually(
    scenario: ScenarioLink,
    folderPath: File,
    examplesDir: File,
    records: MutableMap<String, ExampleSourceRecord>,
): Int {
    val content = fetchPage(scenario.url)
    if (content.isEmpty()) return 0

    val doc = Jsoup.parse(content, BASE_URL)
    var downloaded = 0

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
            if (saveExample(filePath, scenario, examplesDir, records)) downloaded++
        }
    }
    return downloaded
}

private fun processDownloadedFiles(
    folderPath: File,
    scenario: ScenarioLink,
    examplesDir: File,
    records: MutableMap<String, ExampleSourceRecord>,
): Int {
    var count = 0
    for (xmlFile in folderPath.walkTopDown().filter { it.isFile && it.extension.lowercase() == "xml" }) {
        if (saveExample(xmlFile, scenario, examplesDir, records)) count++
    }
    return count
}

fun main(args: Array<String>) {
    val sep = "=".repeat(60)
    println(sep)
    println("IATA NDC Worked Examples Downloader -> ndc_content")
    println(sep)
    println()

    val projectRoot = NdcConstants.projectRoot()
    val contentExamplesDir = NdcConstants.examplesRoot(projectRoot).resolve("files/iata")
    val sourceFile = NdcConstants.examplesRoot(projectRoot).resolve("sources/iata.generated.json")

    sourceFile.parentFile.mkdirs()
    if (contentExamplesDir.exists()) contentExamplesDir.deleteRecursively()
    contentExamplesDir.mkdirs()

    val scenarios = getAllScenarioLinks()
    if (scenarios.isEmpty()) {
        println("No scenarios found!")
        return
    }

    println()
    println("Starting downloads...")
    println("-".repeat(60))

    var successfulScenarios = 0
    var totalFiles = 0
    val recordsById = linkedMapOf<String, ExampleSourceRecord>()

    for ((i, scenario) in scenarios.withIndex()) {
        println("\n[${i + 1}/${scenarios.size}] ${scenario.title}")
        println("  Page ID: ${scenario.pageId}")

        val filesSaved = downloadAttachments(scenario, contentExamplesDir, recordsById)
        if (filesSaved > 0) {
            println("  Processed $filesSaved file(s)")
            totalFiles += filesSaved
            successfulScenarios++
        } else {
            println("  No xml files processed")
        }

        Thread.sleep(1000)
    }

    val examples = recordsById.values.sortedWith(compareBy<ExampleSourceRecord> { it.message }.thenBy { it.id ?: "" })
    val output = ExampleSourcesFile(version = 1, examples = examples)
    sourceFile.writeText(contentJson.encodeToString(output))

    println()
    println(sep)
    println("Download Summary")
    println(sep)
    println("Total scenarios processed: ${scenarios.size}")
    println("Scenarios with downloads: $successfulScenarios")
    println("Total files processed: $totalFiles")
    println("IATA examples dir: ${contentExamplesDir.absolutePath}")
    println("Metadata file: ${sourceFile.absolutePath}")
    println()
}
