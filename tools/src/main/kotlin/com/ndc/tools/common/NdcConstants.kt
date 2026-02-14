package com.ndc.tools.common

import java.io.File

object NdcConstants {
    /** Schema ID (from xsi:schemaLocation URL) → raw schema folder name */
    val RAW_VERSION_MAP = mapOf(
        "2134" to "21.3.5_ndc",
        "2135" to "21.3.5_ndc",
        "2136" to "24.1_ndc",
        "243" to "24.3_ndc",
        "244" to "24.4_ndc",
    )

    /** Schema ID (from xsi:schemaLocation URL) → flattened schema folder name */
    val FLATTENED_VERSION_MAP = mapOf(
        "2134" to "21.3.5",
        "2135" to "21.3.5",
        "2136" to "24.1",
        "243" to "24.3",
        "244" to "24.4",
    )

    /** Schema ID → display version (used by download/metadata scripts) */
    val VERSION_MAP = mapOf(
        "2134" to "21.3.5",
        "2135" to "21.3.5",
        "2136" to "24.1",
        "243" to "24.3",
        "244" to "24.4",
        "245" to "25.4",
    )

    fun projectRoot(): File {
        var dir = File(System.getProperty("user.dir")).canonicalFile
        while (dir.parentFile != null) {
            if (File(dir, "iata_ndc_messages.json").exists()) return dir
            dir = dir.parentFile
        }
        return File(System.getProperty("user.dir")).canonicalFile.parentFile
    }

    fun contentRoot(projectRoot: File = projectRoot()): File = projectRoot.resolve("ndc_content")
    fun examplesRoot(projectRoot: File = projectRoot()): File = contentRoot(projectRoot).resolve("examples")
    fun flowsRoot(projectRoot: File = projectRoot()): File = contentRoot(projectRoot).resolve("flows")

    /** Sanitize a string for use as a folder name */
    fun sanitizeFolderName(name: String): String {
        return name
            .replace(Regex("""[<>:"/\\|?*]"""), "")
            .replace(Regex("""\s+"""), " ")
            .take(100)
            .trim()
    }
}
