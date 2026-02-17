package com.ndc.tools

import com.ndc.tools.common.ExampleCatalogFile
import com.ndc.tools.common.ExampleRecord
import com.ndc.tools.common.ExampleSourceRecord
import com.ndc.tools.common.ExampleSourcesFile
import com.ndc.tools.common.FlowRecord
import com.ndc.tools.common.FlowStepRecord
import com.ndc.tools.common.FlowsFile
import com.ndc.tools.common.IataFlowSourceRecord
import com.ndc.tools.common.IataFlowSourcesFile
import com.ndc.tools.common.NdcConstants
import com.ndc.tools.common.buildExampleId
import com.ndc.tools.common.contentJson
import com.ndc.tools.common.ensureCanonicalXmlPath
import com.ndc.tools.common.toPublicPath
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.encodeToString

data class CatalogBuildInput(
    val projectRoot: File,
    val iataSourceFile: File,
    val customSourceFile: File,
    val flowsFile: File,
    val iataFlowSourceFile: File,
    val outputCatalogFile: File,
    val outputFlowsFile: File,
)

fun defaultCatalogBuildInput(projectRoot: File): CatalogBuildInput {
    val examplesRoot = NdcConstants.examplesRoot(projectRoot)
    return CatalogBuildInput(
        projectRoot = projectRoot,
        iataSourceFile = examplesRoot.resolve("sources/iata.generated.json"),
        customSourceFile = examplesRoot.resolve("sources/custom.json"),
        flowsFile = NdcConstants.flowsRoot(projectRoot).resolve("flows.json"),
        iataFlowSourceFile = NdcConstants.flowSourcesRoot(projectRoot).resolve("iata.generated.json"),
        outputCatalogFile = examplesRoot.resolve("catalog.json"),
        outputFlowsFile = NdcConstants.flowsRoot(projectRoot).resolve("flows.json"),
    )
}

fun ensureContentBootstrapFiles(input: CatalogBuildInput) {
    input.customSourceFile.parentFile.mkdirs()
    if (!input.customSourceFile.exists()) {
        input.customSourceFile.writeText(contentJson.encodeToString(ExampleSourcesFile(version = 1, examples = emptyList())))
    }

    input.flowsFile.parentFile.mkdirs()
    if (!input.flowsFile.exists()) {
        input.flowsFile.writeText(contentJson.encodeToString(FlowsFile(version = 1, flows = emptyList())))
    }
}

private fun readSourceFile(path: File): ExampleSourcesFile {
    if (!path.exists()) return ExampleSourcesFile(version = 1, examples = emptyList())
    return contentJson.decodeFromString(ExampleSourcesFile.serializer(), path.readText())
}

private fun readFlowsFile(path: File): FlowsFile {
    if (!path.exists()) return FlowsFile(version = 1, flows = emptyList())
    return contentJson.decodeFromString(FlowsFile.serializer(), path.readText())
}

private fun readIataFlowSources(path: File): IataFlowSourcesFile {
    if (!path.exists()) return IataFlowSourcesFile(version = 1, flows = emptyList())
    return contentJson.decodeFromString(IataFlowSourcesFile.serializer(), path.readText())
}

fun buildCatalog(input: CatalogBuildInput): ExampleCatalogFile {
    val iata = readSourceFile(input.iataSourceFile)
    val custom = readSourceFile(input.customSourceFile)
    val flows = readFlowsFile(input.flowsFile)

    val allSourceRecords = iata.examples + custom.examples
    val examplesById = linkedMapOf<String, ExampleRecord>()

    for (record in allSourceRecords) {
        val normalized = normalizeRecord(input.projectRoot, record)
        check(!examplesById.containsKey(normalized.id)) {
            "Duplicate example id detected: ${normalized.id}"
        }
        examplesById[normalized.id] = normalized
    }

    val flowIdByExample = mutableMapOf<String, String>()
    val flowIdsSeen = mutableSetOf<String>()

    for (flow in flows.flows) {
        check(flowIdsSeen.add(flow.id)) { "Duplicate flow id detected: ${flow.id}" }

        for (step in flow.steps) {
            if (step.exampleId.isEmpty()) continue
            
            val example = examplesById[step.exampleId]
                ?: error("Flow '${flow.id}' references unknown example_id '${step.exampleId}'")

            check(example.message == step.message) {
                "Flow '${flow.id}' step '${step.stepId}' message '${step.message}' does not match example '${example.id}' message '${example.message}'"
            }

            val assigned = flowIdByExample[example.id]
            check(assigned == null || assigned == flow.id) {
                "Example '${example.id}' is assigned to multiple flows: '$assigned' and '${flow.id}'"
            }
            flowIdByExample[example.id] = flow.id
        }
    }

    val finalExamples = examplesById.values
        .map { record ->
            val explicitFlowId = flowIdByExample[record.id]
            val inferredIataFlowId = inferIataFlowId(record)

            val finalFlowId = when {
                explicitFlowId != null && inferredIataFlowId != null -> {
                    // Explicit assignment (from flows.json) takes precedence over inferred
                    explicitFlowId
                }
                explicitFlowId != null -> explicitFlowId
                else -> inferredIataFlowId
            }

            record.copy(flowId = finalFlowId)
        }
        .sortedWith(compareBy<ExampleRecord> { it.message }.thenBy { it.title }.thenBy { it.id })

    return ExampleCatalogFile(
        version = 1,
        generatedAt = Instant.now().toString(),
        examples = finalExamples,
    )
}

private fun normalizeRecord(projectRoot: File, source: ExampleSourceRecord): ExampleRecord {
    val normalizedSource = source.source.trim().lowercase()
    check(normalizedSource == "iata" || normalizedSource == "custom") {
        "Unsupported source '${source.source}' for message '${source.message}'"
    }

    val normalizedXmlPath = ensureCanonicalXmlPath(source.xmlPath)
    val xmlFile = projectRoot.resolve(normalizedXmlPath)
    check(xmlFile.exists()) {
        "XML file not found for record '${source.message}/${source.fileName}': $normalizedXmlPath"
    }

    val id = source.id ?: buildExampleId(
        source = normalizedSource,
        message = source.message,
        version = source.version,
        sourcePageId = source.sourcePageId,
        sourceUrl = source.sourceUrl,
        fileName = source.fileName,
    )

    return ExampleRecord(
        id = id,
        source = normalizedSource,
        message = source.message,
        version = source.version,
        title = source.title,
        description = source.description,

        fileName = source.fileName,
        xmlPath = normalizedXmlPath,
        publicPath = toPublicPath(normalizedXmlPath),
        sourceUrl = source.sourceUrl,
        sourcePageId = source.sourcePageId,
        flowId = null,
        isActive = source.isActive,
    )
}

private fun inferIataFlowId(record: ExampleRecord): String? {
    if (record.source != "iata") return null

    val pageId = record.sourcePageId?.trim()?.takeUnless { it.isEmpty() }
    if (pageId != null) return "flow_iata_page_$pageId"

    val url = record.sourceUrl?.trim()?.takeUnless { it.isEmpty() } ?: return null
    val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
    val shortHex = digest.joinToString("") { "%02x".format(it) }.take(12)
    return "flow_iata_url_$shortHex"
}

// ── IATA flow source → FlowRecord conversion ──

private fun convertIataFlows(
    iataFlows: IataFlowSourcesFile,
    examplesById: Map<String, ExampleRecord>,
): List<FlowRecord> {
    // Build a lookup: (pageId, message) → list of example IDs
    val pageMessageIndex = mutableMapOf<String, MutableList<ExampleRecord>>()
    for (example in examplesById.values) {
        val pageId = example.sourcePageId ?: continue
        val key = "${pageId}|${example.message}"
        pageMessageIndex.getOrPut(key) { mutableListOf() }.add(example)
    }

    val flows = mutableListOf<FlowRecord>()

    for (iataFlow in iataFlows.flows) {
        if (iataFlow.steps.isEmpty()) {
            // Skip flows without step data — they're sub-scenario pages
            // that inherit their parent's flow structure
            continue
        }

        // Generate SEO-friendly slug from title
        val slug = slugify(iataFlow.title)
        
        // Convert steps, matching to examples where possible
        val flowSteps = mutableListOf<FlowStepRecord>()
        for ((idx, iataStep) in iataFlow.steps.withIndex()) {
            
            // Normalize message name: remove prefix, fix typos, handle Notification->Notif
            var rawMessage = iataStep.message.removePrefix("IATA_").trim()
            
            // Known IATA typos/inconsistencies mapping
            if (rawMessage == "SeatAvailabiltyRQ") {
                rawMessage = "SeatAvailabilityRQ"
            }
            if (rawMessage == "SeatAvailabiltyRS") {
                rawMessage = "SeatAvailabilityRS"
            }
            if (rawMessage == "OrderSalesInformationNotificationRQ") {
                rawMessage = "OrderSalesInformationNotifRQ"
            }

            // Try to find example using the CORRECTED message name first
            // (e.g. flow source has "Notification", example catalog has "Notif")
            var key = "${iataFlow.sourcePageId}|IATA_$rawMessage"
            var matchingExamples = pageMessageIndex[key]

            // Fallback: try original message if no match found (in case catalog has the typo too)
            if (matchingExamples == null) {
                 key = "${iataFlow.sourcePageId}|${iataStep.message}"
                 matchingExamples = pageMessageIndex[key]
            }

            // Pick the best example: prefer the first one ordered by file name
            val exampleId = matchingExamples
                ?.sortedBy { it.fileName }
                ?.firstOrNull()?.id

            val stepId = rawMessage
                .replace(Regex("([a-z])([A-Z])"), "$1_$2")
                .lowercase() + "_${iataStep.stepNumber.replace('.', '_')}"

            // Determine "next" - default is the next step in sequence
            val nextSteps = if (idx < iataFlow.steps.size - 1) {
                val nextStep = iataFlow.steps[idx + 1]
                
                var nextRawMessage = nextStep.message.removePrefix("IATA_").trim()
                if (nextRawMessage == "SeatAvailabiltyRQ") nextRawMessage = "SeatAvailabilityRQ"
                if (nextRawMessage == "SeatAvailabiltyRS") nextRawMessage = "SeatAvailabilityRS"
                if (nextRawMessage == "OrderSalesInformationNotificationRQ") nextRawMessage = "OrderSalesInformationNotifRQ"

                val nextId = nextRawMessage
                    .replace(Regex("([a-z])([A-Z])"), "$1_$2")
                    .lowercase() + "_${nextStep.stepNumber.replace('.', '_')}"
                listOf(nextId)
            } else {
                emptyList()
            }

            flowSteps.add(FlowStepRecord(
                stepId = stepId,
                order = idx + 1,
                message = "IATA_" + rawMessage, // Use corrected name
                exampleId = exampleId ?: "",
                notes = iataStep.description.takeIf { it.isNotEmpty() },
                optional = false,
                next = nextSteps,
            ))
        }

        flows.add(FlowRecord(
            id = slug, // Use slug instead of iataFlow.id
            title = iataFlow.title,
            description = iataFlow.description,
            goal = iataFlow.postconditions ?: "",

            actors = listOf("Seller", "Airline"),
            status = "iata",
            sourceUrl = iataFlow.sourceUrl,
            steps = flowSteps,
        ))
    }

    return flows
}

private fun slugify(input: String): String {
    return input.lowercase()
        .replace(Regex("[^a-z0-9\\s-]"), "") // Remove special chars
        .trim()
        .replace(Regex("\\s+"), "-") // Replace spaces with hyphens
}

private fun mergeFlows(
    iataFlows: List<FlowRecord>,
    customFlows: FlowsFile,
): FlowsFile {
    // Filter out flows that are marked as 'iata' from the customization file
    // This allows us to regenerate IATA flows if the generator logic improves (e.g. message name fixes)
    // while executing manual overrides if someone changed the status to something else (e.g. 'custom')
    val trueCustomFlows = customFlows.flows.filter { it.status != "iata" }
    val customIds = trueCustomFlows.map { it.id }.toSet()
    
    // Custom flows take precedence on ID conflicts
    val mergedFlows = (trueCustomFlows + iataFlows.filter { it.id !in customIds })
        .filter { flow ->
            // Filter out flows with anything less than 100% coverage
            // User requested strict filtering: "I want only those that have 100% messages"
            if (flow.steps.isEmpty()) return@filter true 
            
            val stepsWithExamples = flow.steps.count { it.exampleId.isNotEmpty() }
            
            if (stepsWithExamples < flow.steps.size) {
                val coverage = stepsWithExamples.toDouble() / flow.steps.size
                println("Dropping flow '${flow.title}' due to incomplete coverage (${(coverage * 100).toInt()}%)")
                false
            } else {
                true
            }
        }
        
    return FlowsFile(version = 1, flows = mergedFlows.sortedBy { it.title })
}

fun main(args: Array<String>) {
    val projectRoot = args.firstOrNull { it.startsWith("--project-root=") }
        ?.removePrefix("--project-root=")
        ?.let { File(it) }
        ?: NdcConstants.projectRoot()

    val input = defaultCatalogBuildInput(projectRoot)
    ensureContentBootstrapFiles(input)

    val catalog = buildCatalog(input)
    input.outputCatalogFile.parentFile.mkdirs()
    input.outputCatalogFile.writeText(contentJson.encodeToString(catalog))

    // Build merged flows from IATA sources + custom
    val iataFlowSources = readIataFlowSources(input.iataFlowSourceFile)
    val customFlows = readFlowsFile(input.flowsFile)

    // Need the example index to match steps → examples
    val examplesById = catalog.examples.associateBy { it.id }
    val iataFlows = convertIataFlows(iataFlowSources, examplesById)
    val mergedFlows = mergeFlows(iataFlows, customFlows)

    input.outputFlowsFile.parentFile.mkdirs()
    input.outputFlowsFile.writeText(contentJson.encodeToString(mergedFlows))

    val stepsWithExamples = mergedFlows.flows.flatMap { it.steps }.count { it.exampleId.isNotEmpty() }
    val totalSteps = mergedFlows.flows.sumOf { it.steps.size }

    println("Built catalog with ${catalog.examples.size} examples")
    println("Catalog path: ${input.outputCatalogFile.absolutePath}")
    println("Built flows: ${mergedFlows.flows.size} flows, $totalSteps total steps ($stepsWithExamples with examples)")
    println("Flows path: ${input.outputFlowsFile.absolutePath}")
}
