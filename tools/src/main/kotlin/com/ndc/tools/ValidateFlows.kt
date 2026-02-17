package com.ndc.tools

import com.ndc.tools.common.NdcConstants
import com.ndc.tools.common.contentJson
import com.ndc.tools.common.FlowsFile
import java.io.File

fun main(args: Array<String>) {
    val projectRoot = args.firstOrNull { it.startsWith("--project-root=") }
        ?.removePrefix("--project-root=")
        ?.let { File(it) }
        ?: NdcConstants.projectRoot()

    val flowsFile = NdcConstants.flowsRoot(projectRoot).resolve("flows.json")
    if (!flowsFile.exists()) {
        println("Flows file not found: ${flowsFile.absolutePath}")
        return
    }

    val flowsData = contentJson.decodeFromString(FlowsFile.serializer(), flowsFile.readText())
    
    // Load schema messages from 21.3.5 directory as reference for valid message names
    val schemasRoot = projectRoot.resolve("ndc_schemas")
    val schemaDir = schemasRoot.resolve("21.3.5")
    
    // Fallback: check raw listing if directory structure is different
    val validMessages = if (schemaDir.exists()) {
        schemaDir.listFiles()?.filter { it.isDirectory }?.map { it.name }?.toSet() ?: emptySet()
    } else {
        // Try looking in public/schemas if ndc_schemas isn't built yet or is elsewhere
        val publicSchemas = projectRoot.resolve("site/public/schemas/21.3.5")
        publicSchemas.listFiles()?.filter { it.isDirectory }?.map { it.name }?.toSet() ?: emptySet()
    }

    println("Validating against ${validMessages.size} schema messages from 21.3.5...")
    if (validMessages.isEmpty()) {
        println("WARNING: No schema messages found for validation. Check path.")
         // list parent dir to debug
         println("Schema root contents: ${schemasRoot.list()?.joinToString()}")
    }

    var errorCount = 0

    flowsData.flows.forEach { flow ->
        flow.steps.forEach { step ->
            val messageName = step.message.removePrefix("IATA_")
            
            if (!validMessages.contains(messageName)) {
                println("[ERROR] Flow '${flow.title}' step ${step.order}: Message '$messageName' not found in schema.")
                
                // Check if 'Notification' -> 'Notif' fixes it
                val correction = messageName.replace("Notification", "Notif")
                if (validMessages.contains(correction)) {
                     println("        -> Did you mean '$correction'?")
                }
                errorCount++
            }
        }
    }

    if (errorCount > 0) {
        println("\nFound $errorCount errors.")
        System.exit(1)
    } else {
        println("\nValidation passed!")
    }
}
