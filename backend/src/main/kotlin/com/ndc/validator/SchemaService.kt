package com.ndc.validator

import java.io.File

data class SchemaRef(val version: String, val message: String, val path: File)

class SchemaService(rootPath: File? = null) {
    private val schemaRoot: File = rootPath ?: File(System.getenv("SCHEMA_ROOT") ?: "src/main/resources/schemas")
    private val schemas = mutableListOf<SchemaRef>()

    init {
        indexSchemas()
    }

    private fun indexSchemas() {
        println("Indexing schemas from: \${schemaRoot.absolutePath}")
        if (!schemaRoot.exists()) {
            println("Schema root not found: \${schemaRoot.absolutePath}")
            return
        }

        schemaRoot.listFiles()?.filter { it.isDirectory }?.forEach { versionDir ->
            println("Scanning version: \${versionDir.name}")
            // Look for message directories in version directory
            versionDir.listFiles()?.filter { it.isDirectory }?.forEach { messageDir ->
                // The main XSD should be directly in the message directory
                // or prefixed with IATA_
                val messageName = messageDir.name
                println("Checking message dir: $messageName")
                val files = messageDir.listFiles()?.map { it.name } ?: emptyList()
                println("Files in $messageName: $files")
                
                val mainXsd = messageDir.listFiles()?.find { 
                    it.name == "${messageName}.xsd" || it.name == "IATA_${messageName}.xsd" 
                }

                if (mainXsd != null) {
                    println("Found schema: \${versionDir.name}/$messageName -> \${mainXsd.name}")
                    schemas.add(SchemaRef(versionDir.name, messageName, mainXsd))
                } else {
                    println("No main XSD found for $messageName in \${messageDir.absolutePath}")
                }
            }
        }
        println("Indexed \${schemas.size} schemas.")
    }

    fun getSchema(version: String, message: String): File? {
        return schemas.find { it.version == version && it.message == message }?.path
    }

    fun listSchemas(): Map<String, List<String>> {
        return schemas.groupBy { it.version }
            .mapValues { entry -> entry.value.map { it.message }.sorted() }
    }

    fun getSchemaFiles(version: String, message: String): List<File>? {
        val schemaRef = schemas.find { it.version == version && it.message == message } ?: return null
        return schemaRef.path.parentFile.listFiles()?.filter { it.isFile && (it.name.endsWith(".xsd") || it.name.endsWith(".xml")) }?.toList()
    }

    fun getSchemasForVersion(version: String): Map<String, File> {
        return schemas.filter { it.version == version }
            .associate { it.message to it.path }
    }
}
