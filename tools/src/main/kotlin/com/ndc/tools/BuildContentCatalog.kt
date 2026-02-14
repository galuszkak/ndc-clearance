package com.ndc.tools

import com.ndc.tools.common.ExampleCatalogFile
import com.ndc.tools.common.ExampleRecord
import com.ndc.tools.common.ExampleSourceRecord
import com.ndc.tools.common.ExampleSourcesFile
import com.ndc.tools.common.FlowsFile
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
    val outputCatalogFile: File,
)

fun defaultCatalogBuildInput(projectRoot: File): CatalogBuildInput {
    val examplesRoot = NdcConstants.examplesRoot(projectRoot)
    return CatalogBuildInput(
        projectRoot = projectRoot,
        iataSourceFile = examplesRoot.resolve("sources/iata.generated.json"),
        customSourceFile = examplesRoot.resolve("sources/custom.json"),
        flowsFile = NdcConstants.flowsRoot(projectRoot).resolve("flows.json"),
        outputCatalogFile = examplesRoot.resolve("catalog.json"),
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
                    check(explicitFlowId == inferredIataFlowId) {
                        "Example '${record.id}' has conflicting flow assignment: explicit '$explicitFlowId' vs inferred '$inferredIataFlowId'"
                    }
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
        tags = source.tags,
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

    println("Built catalog with ${catalog.examples.size} examples")
    println("Catalog path: ${input.outputCatalogFile.absolutePath}")
}
