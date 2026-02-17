package com.ndc.tools

import com.ndc.tools.common.FlowsFile
import com.ndc.tools.common.contentJson
import java.io.File

fun main() {
    val projectDir = File(System.getProperty("user.dir"))
    val flowsFile = projectDir.resolve("../ndc_content/flows/flows.json")

    println("Verifying flow coverage from: ${flowsFile.absolutePath}")

    if (!flowsFile.exists()) {
        error("flows.json not found at ${flowsFile.absolutePath}")
    }

    val flowsData = contentJson.decodeFromString<FlowsFile>(flowsFile.readText())
    var totalFlows = 0
    var flowsWithMissingExamples = 0
    var totalSteps = 0
    var stepsWithMissingExamples = 0

    println("\n--- Flow Coverage Report ---\n")

    flowsData.flows.forEach { flow ->
        totalFlows++
        var flowHasMissing = false
        val missingSteps = mutableListOf<String>()

        flow.steps.forEach { step ->
            totalSteps++
            if (step.exampleId.isEmpty()) {
                stepsWithMissingExamples++
                flowHasMissing = true
                missingSteps.add("Step ${step.order} (${step.message})")
            }
        }

        if (flowHasMissing) {
            flowsWithMissingExamples++
            val flowStepsCount = flow.steps.size
            val missingCount = missingSteps.size
            val coverage = ((flowStepsCount - missingCount).toDouble() / flowStepsCount * 100)
            
            println("Flow: ${flow.title} (ID: ${flow.id})")
            println("  Coverage: ${coverage.format(1)}% ($missingCount/$flowStepsCount missing)")
            println("  Missing examples for: ${missingSteps.joinToString(", ")}")
        }
    }

    println("\n--- Summary ---")
    println("Total Flows: $totalFlows")
    println("Flows with missing examples: $flowsWithMissingExamples")
    println("Total Steps: $totalSteps")
    println("Steps with missing examples: $stepsWithMissingExamples")
    println("Coverage: ${((totalSteps - stepsWithMissingExamples).toDouble() / totalSteps * 100).format(1)}%")

    if (stepsWithMissingExamples > 0) {
        println("\nFAILURE: Found $stepsWithMissingExamples steps with missing XML examples.")
        System.exit(1)
    } else {
        println("\nSUCCESS: All flow steps have associated XML examples.")
    }
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)
