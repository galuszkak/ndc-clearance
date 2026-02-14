package com.ndc.tools.common

import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val CONTENT_XML_PREFIX = "ndc_content/examples/files/"

val contentJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class ExampleSourcesFile(
    val version: Int = 1,
    val examples: List<ExampleSourceRecord> = emptyList(),
)

@Serializable
data class ExampleSourceRecord(
    val id: String? = null,
    val source: String,
    val message: String,
    val version: String,
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("file_name")
    val fileName: String,
    @SerialName("xml_path")
    val xmlPath: String,
    @SerialName("source_url")
    val sourceUrl: String? = null,
    @SerialName("source_page_id")
    val sourcePageId: String? = null,
    @SerialName("is_active")
    val isActive: Boolean = true,
)

@Serializable
data class ExampleCatalogFile(
    val version: Int = 1,
    @SerialName("generated_at")
    val generatedAt: String,
    val examples: List<ExampleRecord>,
)

@Serializable
data class ExampleRecord(
    val id: String,
    val source: String,
    val message: String,
    val version: String,
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("file_name")
    val fileName: String,
    @SerialName("xml_path")
    val xmlPath: String,
    @SerialName("public_path")
    val publicPath: String,
    @SerialName("source_url")
    val sourceUrl: String? = null,
    @SerialName("source_page_id")
    val sourcePageId: String? = null,
    @SerialName("flow_id")
    val flowId: String? = null,
    @SerialName("is_active")
    val isActive: Boolean = true,
)

@Serializable
data class FlowsFile(
    val version: Int = 1,
    val flows: List<FlowRecord> = emptyList(),
)

@Serializable
data class FlowRecord(
    val id: String,
    val title: String,
    val description: String,
    val goal: String,
    val tags: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    val status: String = "draft",
    val steps: List<FlowStepRecord> = emptyList(),
)

@Serializable
data class FlowStepRecord(
    @SerialName("step_id")
    val stepId: String,
    val order: Int,
    val message: String,
    @SerialName("example_id")
    val exampleId: String,
    val notes: String? = null,
    val optional: Boolean = false,
)

fun buildExampleId(
    source: String,
    message: String,
    version: String,
    sourcePageId: String?,
    sourceUrl: String?,
    fileName: String,
): String {
    val seed = listOf(
        source.trim().lowercase(),
        message.trim(),
        version.trim(),
        (sourcePageId?.trim().takeUnless { it.isNullOrEmpty() } ?: sourceUrl?.trim().orEmpty()),
        fileName.trim(),
    ).joinToString("|")

    val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
    val shortHex = digest.joinToString("") { "%02x".format(it) }.take(12)
    return "ex_$shortHex"
}

fun ensureCanonicalXmlPath(path: String): String {
    val normalized = path.replace('\\', '/').trimStart('/')
    if (normalized.startsWith(CONTENT_XML_PREFIX)) return normalized
    val fileName = normalized.substringAfterLast('/')
    return "$CONTENT_XML_PREFIX$fileName"
}

fun toPublicPath(xmlPath: String): String {
    val normalized = ensureCanonicalXmlPath(xmlPath)
    val relative = normalized.removePrefix("ndc_content/")
    return "/content/$relative"
}
