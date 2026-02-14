package com.ndc.tools

import com.ndc.tools.common.ExampleSourceRecord
import com.ndc.tools.common.ExampleSourcesFile
import com.ndc.tools.common.FlowRecord
import com.ndc.tools.common.FlowStepRecord
import com.ndc.tools.common.FlowsFile
import com.ndc.tools.common.buildExampleId
import com.ndc.tools.common.contentJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BuildContentCatalogTest {

    private fun tempDir(): File = kotlin.io.path.createTempDirectory("ndc-content-test").toFile()

    private fun write(file: File, content: String) {
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    @Test
    fun `buildExampleId is deterministic`() {
        val a = buildExampleId("iata", "IATA_OrderCreateRQ", "24.1", "123", "https://example.test", "foo.xml")
        val b = buildExampleId("iata", "IATA_OrderCreateRQ", "24.1", "123", "https://example.test", "foo.xml")
        assertEquals(a, b)
        assertTrue(a.startsWith("ex_"))
    }

    @Test
    fun `buildCatalog populates flow_id for referenced examples`() {
        val root = tempDir()
        val input = defaultCatalogBuildInput(root)

        val xmlPath = root.resolve("ndc_content/examples/files/iata/ex_abc.xml")
        write(xmlPath, "<IATA_OrderCreateRQ/>")

        val iata = ExampleSourcesFile(
            examples = listOf(
                ExampleSourceRecord(
                    id = "ex_abc",
                    source = "iata",
                    message = "IATA_OrderCreateRQ",
                    version = "24.1",
                    title = "Sample",
                    fileName = "sample.xml",
                    xmlPath = "ndc_content/examples/files/iata/ex_abc.xml",
                ),
            ),
        )
        write(input.iataSourceFile, contentJson.encodeToString(ExampleSourcesFile.serializer(), iata))
        write(input.customSourceFile, contentJson.encodeToString(ExampleSourcesFile.serializer(), ExampleSourcesFile()))

        val flows = FlowsFile(
            flows = listOf(
                FlowRecord(
                    id = "flow_a",
                    title = "A",
                    description = "A",
                    goal = "A",
                    steps = listOf(
                        FlowStepRecord(
                            stepId = "s1",
                            order = 1,
                            message = "IATA_OrderCreateRQ",
                            exampleId = "ex_abc",
                        ),
                    ),
                ),
            ),
        )
        write(input.flowsFile, contentJson.encodeToString(FlowsFile.serializer(), flows))

        val catalog = buildCatalog(input)
        assertEquals(1, catalog.examples.size)
        assertEquals("flow_a", catalog.examples.first().flowId)
    }

    @Test
    fun `buildCatalog infers iata flow_id from source_page_id when no manual flow is provided`() {
        val root = tempDir()
        val input = defaultCatalogBuildInput(root)

        write(root.resolve("ndc_content/examples/files/iata/ex_iata.xml"), "<IATA_OrderCreateRQ/>")

        val iata = ExampleSourcesFile(
            examples = listOf(
                ExampleSourceRecord(
                    id = "ex_iata",
                    source = "iata",
                    message = "IATA_OrderCreateRQ",
                    version = "24.1",
                    title = "IATA Example",
                    fileName = "sample.xml",
                    xmlPath = "ndc_content/examples/files/iata/ex_iata.xml",
                    sourcePageId = "123456",
                ),
            ),
        )
        write(input.iataSourceFile, contentJson.encodeToString(ExampleSourcesFile.serializer(), iata))
        write(input.customSourceFile, contentJson.encodeToString(ExampleSourcesFile.serializer(), ExampleSourcesFile()))
        write(input.flowsFile, contentJson.encodeToString(FlowsFile.serializer(), FlowsFile()))

        val catalog = buildCatalog(input)
        assertEquals("flow_iata_page_123456", catalog.examples.single().flowId)
    }

    @Test
    fun `buildCatalog fails on duplicate example ids`() {
        val root = tempDir()
        val input = defaultCatalogBuildInput(root)

        write(root.resolve("ndc_content/examples/files/iata/ex_same.xml"), "<A/>")

        val iata = ExampleSourcesFile(
            examples = listOf(
                ExampleSourceRecord(
                    id = "ex_same",
                    source = "iata",
                    message = "IATA_AirShoppingRQ",
                    version = "24.1",
                    title = "One",
                    fileName = "one.xml",
                    xmlPath = "ndc_content/examples/files/iata/ex_same.xml",
                ),
            ),
        )
        val custom = ExampleSourcesFile(
            examples = listOf(
                ExampleSourceRecord(
                    id = "ex_same",
                    source = "custom",
                    message = "IATA_AirShoppingRQ",
                    version = "24.1",
                    title = "Two",
                    fileName = "two.xml",
                    xmlPath = "ndc_content/examples/files/iata/ex_same.xml",
                ),
            ),
        )

        write(input.iataSourceFile, contentJson.encodeToString(ExampleSourcesFile.serializer(), iata))
        write(input.customSourceFile, contentJson.encodeToString(ExampleSourcesFile.serializer(), custom))
        write(input.flowsFile, contentJson.encodeToString(FlowsFile.serializer(), FlowsFile()))

        assertFailsWith<IllegalStateException> { buildCatalog(input) }
    }

    @Test
    fun `buildCatalog fails when flow message does not match example message`() {
        val root = tempDir()
        val input = defaultCatalogBuildInput(root)

        write(root.resolve("ndc_content/examples/files/iata/ex_msg.xml"), "<A/>")

        val iata = ExampleSourcesFile(
            examples = listOf(
                ExampleSourceRecord(
                    id = "ex_msg",
                    source = "iata",
                    message = "IATA_OrderCreateRQ",
                    version = "24.1",
                    title = "One",
                    fileName = "one.xml",
                    xmlPath = "ndc_content/examples/files/iata/ex_msg.xml",
                ),
            ),
        )
        val flows = FlowsFile(
            flows = listOf(
                FlowRecord(
                    id = "flow_bad",
                    title = "Bad",
                    description = "Bad",
                    goal = "Bad",
                    steps = listOf(
                        FlowStepRecord(
                            stepId = "s1",
                            order = 1,
                            message = "IATA_OrderChangeRQ",
                            exampleId = "ex_msg",
                        ),
                    ),
                ),
            ),
        )

        write(input.iataSourceFile, contentJson.encodeToString(ExampleSourcesFile.serializer(), iata))
        write(input.customSourceFile, contentJson.encodeToString(ExampleSourcesFile.serializer(), ExampleSourcesFile()))
        write(input.flowsFile, contentJson.encodeToString(FlowsFile.serializer(), flows))

        assertFailsWith<IllegalStateException> { buildCatalog(input) }
    }

    @Test
    fun `buildCatalog fails when xml file is missing`() {
        val root = tempDir()
        val input = defaultCatalogBuildInput(root)

        val iata = ExampleSourcesFile(
            examples = listOf(
                ExampleSourceRecord(
                    id = "ex_missing",
                    source = "iata",
                    message = "IATA_OrderCreateRQ",
                    version = "24.1",
                    title = "Missing XML",
                    fileName = "missing.xml",
                    xmlPath = "ndc_content/examples/files/iata/ex_missing.xml",
                ),
            ),
        )

        write(input.iataSourceFile, contentJson.encodeToString(ExampleSourcesFile.serializer(), iata))
        write(input.customSourceFile, contentJson.encodeToString(ExampleSourcesFile.serializer(), ExampleSourcesFile()))
        write(input.flowsFile, contentJson.encodeToString(FlowsFile.serializer(), FlowsFile()))

        assertFailsWith<IllegalStateException> { buildCatalog(input) }
    }

    @Test
    fun `buildCatalog fails when flow references unknown example id`() {
        val root = tempDir()
        val input = defaultCatalogBuildInput(root)

        write(root.resolve("ndc_content/examples/files/iata/ex_known.xml"), "<A/>")

        val iata = ExampleSourcesFile(
            examples = listOf(
                ExampleSourceRecord(
                    id = "ex_known",
                    source = "iata",
                    message = "IATA_OrderCreateRQ",
                    version = "24.1",
                    title = "Known",
                    fileName = "known.xml",
                    xmlPath = "ndc_content/examples/files/iata/ex_known.xml",
                ),
            ),
        )
        val flows = FlowsFile(
            flows = listOf(
                FlowRecord(
                    id = "flow_unknown_ref",
                    title = "Bad Ref",
                    description = "Bad Ref",
                    goal = "Bad Ref",
                    steps = listOf(
                        FlowStepRecord(
                            stepId = "s1",
                            order = 1,
                            message = "IATA_OrderCreateRQ",
                            exampleId = "ex_unknown",
                        ),
                    ),
                ),
            ),
        )

        write(input.iataSourceFile, contentJson.encodeToString(ExampleSourcesFile.serializer(), iata))
        write(input.customSourceFile, contentJson.encodeToString(ExampleSourcesFile.serializer(), ExampleSourcesFile()))
        write(input.flowsFile, contentJson.encodeToString(FlowsFile.serializer(), flows))

        assertFailsWith<IllegalStateException> { buildCatalog(input) }
    }

    @Test
    fun `buildCatalog fails when the same example is assigned to multiple flows`() {
        val root = tempDir()
        val input = defaultCatalogBuildInput(root)

        write(root.resolve("ndc_content/examples/files/iata/ex_multi.xml"), "<A/>")

        val iata = ExampleSourcesFile(
            examples = listOf(
                ExampleSourceRecord(
                    id = "ex_multi",
                    source = "iata",
                    message = "IATA_OrderCreateRQ",
                    version = "24.1",
                    title = "Known",
                    fileName = "known.xml",
                    xmlPath = "ndc_content/examples/files/iata/ex_multi.xml",
                ),
            ),
        )
        val flows = FlowsFile(
            flows = listOf(
                FlowRecord(
                    id = "flow_one",
                    title = "One",
                    description = "One",
                    goal = "One",
                    steps = listOf(
                        FlowStepRecord(
                            stepId = "s1",
                            order = 1,
                            message = "IATA_OrderCreateRQ",
                            exampleId = "ex_multi",
                        ),
                    ),
                ),
                FlowRecord(
                    id = "flow_two",
                    title = "Two",
                    description = "Two",
                    goal = "Two",
                    steps = listOf(
                        FlowStepRecord(
                            stepId = "s1",
                            order = 1,
                            message = "IATA_OrderCreateRQ",
                            exampleId = "ex_multi",
                        ),
                    ),
                ),
            ),
        )

        write(input.iataSourceFile, contentJson.encodeToString(ExampleSourcesFile.serializer(), iata))
        write(input.customSourceFile, contentJson.encodeToString(ExampleSourcesFile.serializer(), ExampleSourcesFile()))
        write(input.flowsFile, contentJson.encodeToString(FlowsFile.serializer(), flows))

        assertFailsWith<IllegalStateException> { buildCatalog(input) }
    }
}
