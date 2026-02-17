package com.ndc.tools

import com.ndc.tools.common.ExampleSourceRecord
import com.ndc.tools.common.ExampleSourcesFile
import com.ndc.tools.common.IataFlowSourceRecord
import com.ndc.tools.common.IataFlowSourcesFile
import com.ndc.tools.common.IataFlowStepSource
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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

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

// ── Flow metadata scraping ──

private fun extractSectionText(doc: Document, sectionName: String): String? {
    // Find h2 elements matching the section name
    val header = doc.select("h1, h2, h3").firstOrNull { el ->
        el.text().trim().equals(sectionName, ignoreCase = true)
    } ?: return null

    val parts = mutableListOf<String>()
    var sibling = header.nextElementSibling()
    while (sibling != null && sibling.tagName() !in listOf("h1", "h2")) {
        val text = sibling.text().trim()
        if (text.isNotEmpty()) parts.add(text)
        sibling = sibling.nextElementSibling()
    }
    return parts.joinToString("\n").trim().takeIf { it.isNotEmpty() }
}

private fun extractMainFlowSteps(doc: Document): List<IataFlowStepSource> {
    // Find the Main Flow section header
    val header = doc.select("h1, h2, h3").firstOrNull { el ->
        el.text().trim().equals("Main Flow", ignoreCase = true)
    } ?: return emptyList()

    // Look for a table after the header
    var sibling = header.nextElementSibling()
    var table: Element? = null
    while (sibling != null && sibling.tagName() !in listOf("h1", "h2")) {
        if (sibling.tagName() == "table") {
            table = sibling
            break
        }
        // Also check for tables nested inside divs
        val nested = sibling.selectFirst("table")
        if (nested != null) {
            table = nested
            break
        }
        sibling = sibling.nextElementSibling()
    }

    if (table == null) {
        // Fallback: look for any table on the page with Step/Message/Description columns
        table = doc.select("table").firstOrNull { t ->
            val headers = t.select("th, thead td").map { it.text().trim().lowercase() }
            headers.containsAll(listOf("step", "message"))
        }
    }

    if (table == null) return emptyList()

    val steps = mutableListOf<IataFlowStepSource>()
    val rows = table.select("tr")

    for (row in rows) {
        val cells = row.select("td")
        if (cells.size < 2) continue

        val stepNum = cells[0].text().trim()
        val message = cells[1].text().trim()
        val description = if (cells.size >= 3) cells[2].text().trim() else ""

        // Skip header rows or condition-only rows
        if (stepNum.isEmpty() || message.isEmpty()) continue
        if (stepNum.equals("step", ignoreCase = true)) continue
        if (message.equals("condition", ignoreCase = true)) continue

        // Normalize the message name: add IATA_ prefix if not present
        val normalizedMessage = if (message.startsWith("IATA_")) message
            else "IATA_$message"

        steps.add(IataFlowStepSource(
            stepNumber = stepNum,
            message = normalizedMessage,
            description = description,
        ))
    }
    return steps
}

fun scrapeFlowMetadata(scenario: ScenarioLink, html: String): IataFlowSourceRecord? {
    if (html.isEmpty()) return null

    val doc = Jsoup.parse(html, BASE_URL)

    val description = extractSectionText(doc, "Description")
    if (description.isNullOrBlank()) {
        println("    No Description section found, skipping flow")
        return null
    }

    val steps = extractMainFlowSteps(doc)
    val preconditions = extractSectionText(doc, "Preconditions")
    val postconditions = extractSectionText(doc, "Postconditions")

    return IataFlowSourceRecord(
        id = "flow_iata_page_${scenario.pageId}",
        title = scenario.title,
        description = description,
        sourceUrl = scenario.url,
        sourcePageId = scenario.pageId,
        preconditions = preconditions,
        postconditions = postconditions,
        steps = steps,
    )
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

/**
 * Result of processing a single scenario: examples downloaded + optional flow metadata.
 * We cache the fetched page HTML so we don't need to re-fetch for flow scraping.
 */
data class ScenarioResult(
    val filesSaved: Int,
    val pageHtml: String?,
)

private fun downloadAttachments(
    scenario: ScenarioLink,
    examplesDir: File,
    records: MutableMap<String, ExampleSourceRecord>,
): ScenarioResult {
    val tempDir = createTempDirectory("ndc-iata-${scenario.pageId}-").toFile()
    var cachedHtml: String? = null
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
                if (fromZip > 0) {
                    // Fetch page for flow scraping (zip didn't require page fetch)
                    cachedHtml = fetchPage(scenario.url)
                    return ScenarioResult(fromZip, cachedHtml)
                }
            } catch (_: Exception) {
                println("    Warning: Not a valid zip, trying individual downloads")
                zipPath.delete()
            }
        }

        val result = downloadAttachmentsIndividuallyWithHtml(scenario, tempDir, examplesDir, records)
        return result
    } finally {
        tempDir.deleteRecursively()
    }
}

private fun downloadAttachmentsIndividuallyWithHtml(
    scenario: ScenarioLink,
    folderPath: File,
    examplesDir: File,
    records: MutableMap<String, ExampleSourceRecord>,
): ScenarioResult {
    val content = fetchPage(scenario.url)
    if (content.isEmpty()) return ScenarioResult(0, null)

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
    return ScenarioResult(downloaded, content)
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
    println("IATA NDC Worked Examples & Flows Downloader -> ndc_content")
    println(sep)
    println()

    val projectRoot = NdcConstants.projectRoot()
    val contentExamplesDir = NdcConstants.examplesRoot(projectRoot).resolve("files/iata")
    val sourceFile = NdcConstants.examplesRoot(projectRoot).resolve("sources/iata.generated.json")
    val flowSourcesDir = NdcConstants.flowSourcesRoot(projectRoot)
    val flowSourceFile = flowSourcesDir.resolve("iata.generated.json")

    sourceFile.parentFile.mkdirs()
    flowSourcesDir.mkdirs()
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
    val flowRecords = mutableListOf<IataFlowSourceRecord>()

    for ((i, scenario) in scenarios.withIndex()) {
        println("\n[${i + 1}/${scenarios.size}] ${scenario.title}")
        println("  Page ID: ${scenario.pageId}")

        val result = downloadAttachments(scenario, contentExamplesDir, recordsById)
        if (result.filesSaved > 0) {
            println("  Processed ${result.filesSaved} file(s)")
            totalFiles += result.filesSaved
            successfulScenarios++
        } else {
            println("  No xml files processed")
        }

        // Scrape flow metadata from the page
        val html = result.pageHtml ?: fetchPage(scenario.url)
        val flowRecord = scrapeFlowMetadata(scenario, html)
        if (flowRecord != null) {
            println("  Flow: ${flowRecord.steps.size} steps scraped")
            flowRecords.add(flowRecord)
        } else {
            println("  Flow: no flow metadata found")
        }

        Thread.sleep(1000)
    }

    // Write example sources
    val examples = recordsById.values.sortedWith(compareBy<ExampleSourceRecord> { it.message }.thenBy { it.id ?: "" })
    val output = ExampleSourcesFile(version = 1, examples = examples)
    sourceFile.writeText(contentJson.encodeToString(output))

    // Write flow sources
    val flowOutput = IataFlowSourcesFile(version = 1, flows = flowRecords.sortedBy { it.title })
    flowSourceFile.writeText(contentJson.encodeToString(flowOutput))

    println()
    println(sep)
    println("Download Summary")
    println(sep)
    println("Total scenarios processed: ${scenarios.size}")
    println("Scenarios with downloads: $successfulScenarios")
    println("Total files processed: $totalFiles")
    println("Flows scraped: ${flowRecords.size}")
    println("IATA examples dir: ${contentExamplesDir.absolutePath}")
    println("Metadata file: ${sourceFile.absolutePath}")
    println("Flow sources file: ${flowSourceFile.absolutePath}")
    println()
}
