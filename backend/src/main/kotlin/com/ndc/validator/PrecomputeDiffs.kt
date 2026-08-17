package com.ndc.validator

import java.io.File
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Precomputes schema diffs for every ascending version pair and writes them as
 * gzipped static JSON files (diff_{from}_to_{to}.json.gz), so the site can
 * serve diffs without a live backend. The JSON shape matches GET /api/diff;
 * gzip keeps the corpus ~110 MB instead of ~1.2 GB raw. The browser inflates
 * with the native DecompressionStream API.
 */
fun main(args: Array<String>) {
    val schemaRoot = File(args.getOrElse(0) { "../ndc_schemas" })
    val outputDir = File(args.getOrElse(1) { "../ndc_diffs" })
    require(schemaRoot.isDirectory) { "Schema root not found: ${schemaRoot.absolutePath}" }
    outputDir.mkdirs()

    val schemaService = SchemaService(schemaRoot)
    val diffService = SchemaDiffService(schemaService)
    val versions = schemaService.listSchemas().keys.sorted()

    var count = 0
    for (i in versions.indices) {
        for (j in i + 1 until versions.size) {
            val from = versions[i]
            val to = versions[j]
            val start = System.currentTimeMillis()
            val diffs = diffService.compareVersions(from, to)
            val outFile = File(outputDir, "diff_${from}_to_${to}.json.gz")
            GZIPOutputStream(outFile.outputStream()).bufferedWriter().use {
                it.write(Json.encodeToString(diffs))
            }
            count++
            println("Wrote ${outFile.name} (${diffs.size} messages, ${System.currentTimeMillis() - start} ms)")
        }
    }
    println("Precomputed $count diff files into ${outputDir.absolutePath}")
}
