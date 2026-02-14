package com.ndc.validator

import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ExampleCatalog(
    val version: Int = 1,
    @SerialName("generated_at")
    val generatedAt: String = "",
    val examples: List<ExampleRecord> = emptyList(),
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

class ExampleService(rootPath: File? = null) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val contentRoot: File = rootPath ?: File(System.getenv("CONTENT_ROOT") ?: "src/main/resources/content")
    private val catalogPath = contentRoot.resolve("examples/catalog.json")

    @Volatile
    private var catalog: ExampleCatalog = ExampleCatalog()

    init {
        reload()
    }

    fun reload() {
        if (!catalogPath.exists()) {
            println("Examples catalog not found: ${catalogPath.absolutePath}")
            catalog = ExampleCatalog()
            return
        }

        catalog = try {
            json.decodeFromString(ExampleCatalog.serializer(), catalogPath.readText())
        } catch (e: Exception) {
            println("Failed to load examples catalog: ${e.message}")
            ExampleCatalog()
        }
    }

    fun listExamples(
        message: String? = null,
        version: String? = null,
        source: String? = null,
        flowId: String? = null,
    ): List<ExampleRecord> {
        val normalizedMessage = message?.trim()?.takeUnless { it.isEmpty() }
        val messageCandidates = normalizedMessage?.let {
            setOf(
                it,
                if (it.startsWith("IATA_")) it.removePrefix("IATA_") else "IATA_$it",
            )
        }

        val normalizedSource = source?.trim()?.lowercase()?.takeUnless { it.isEmpty() }
        val normalizedVersion = version?.trim()?.takeUnless { it.isEmpty() }
        val normalizedFlowId = flowId?.trim()?.takeUnless { it.isEmpty() }

        return catalog.examples
            .asSequence()
            .filter { it.isActive }
            .filter { messageCandidates == null || it.message in messageCandidates }
            .filter { normalizedVersion == null || it.version == normalizedVersion }
            .filter { normalizedSource == null || it.source == normalizedSource }
            .filter { normalizedFlowId == null || it.flowId == normalizedFlowId }
            .sortedWith(compareBy<ExampleRecord> { it.message }.thenBy { it.version }.thenBy { it.id })
            .toList()
    }

    fun getExampleById(exampleId: String): ExampleRecord? {
        val id = exampleId.trim()
        if (id.isEmpty()) return null
        return catalog.examples.firstOrNull { it.id == id }
    }

    fun getExampleContent(exampleId: String): String? {
        val example = getExampleById(exampleId) ?: return null
        val xmlFile = resolveXmlPath(example.xmlPath)
        if (!xmlFile.exists() || !xmlFile.isFile) return null
        return xmlFile.readText()
    }

    private fun resolveXmlPath(xmlPath: String): File {
        val normalized = xmlPath.replace('\\', '/')
        val relative = if (normalized.startsWith("ndc_content/")) {
            normalized.removePrefix("ndc_content/")
        } else {
            normalized
        }
        return contentRoot.resolve(relative)
    }
}
