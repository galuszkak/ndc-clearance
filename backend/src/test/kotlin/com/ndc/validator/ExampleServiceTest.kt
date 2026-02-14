package com.ndc.validator

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ExampleServiceTest {

    private val json = Json { prettyPrint = true }

    private fun tempDir(): File = kotlin.io.path.createTempDirectory("example-service-test").toFile()

    private fun write(file: File, content: String) {
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    @Test
    fun `listExamples supports message prefix variants and filters`() {
        val root = tempDir()

        write(root.resolve("examples/files/iata/ex_1.xml"), "<IATA_OrderCreateRQ/>")
        write(root.resolve("examples/files/custom/ex_2.xml"), "<IATA_OrderCreateRQ/>")
        write(root.resolve("examples/files/iata/ex_3.xml"), "<IATA_OrderCreateRQ/>")

        val catalog = ExampleCatalog(
            version = 1,
            generatedAt = "2026-02-14T00:00:00Z",
            examples = listOf(
                ExampleRecord(
                    id = "ex_1",
                    source = "iata",
                    message = "IATA_OrderCreateRQ",
                    version = "24.1",
                    title = "sample",
                    fileName = "sample.xml",
                    xmlPath = "ndc_content/examples/files/iata/ex_1.xml",
                    publicPath = "/content/examples/files/iata/ex_1.xml",
                ),
                ExampleRecord(
                    id = "ex_2",
                    source = "custom",
                    message = "OrderCreateRQ",
                    version = "24.1",
                    title = "custom",
                    fileName = "custom.xml",
                    xmlPath = "ndc_content/examples/files/custom/ex_2.xml",
                    publicPath = "/content/examples/files/custom/ex_2.xml",
                    flowId = "flow_booking",
                ),
                ExampleRecord(
                    id = "ex_3",
                    source = "iata",
                    message = "IATA_OrderCreateRQ",
                    version = "24.1",
                    title = "inactive",
                    fileName = "inactive.xml",
                    xmlPath = "ndc_content/examples/files/iata/ex_3.xml",
                    publicPath = "/content/examples/files/iata/ex_3.xml",
                    isActive = false,
                ),
            ),
        )
        write(root.resolve("examples/catalog.json"), json.encodeToString(ExampleCatalog.serializer(), catalog))

        val service = ExampleService(root)

        assertEquals(2, service.listExamples("IATA_OrderCreateRQ").size)
        assertEquals(2, service.listExamples("OrderCreateRQ").size)
        assertEquals(2, service.listExamples("OrderCreateRQ", version = "24.1").size)
        assertEquals(1, service.listExamples("OrderCreateRQ", source = "custom").size)
        assertEquals(1, service.listExamples("OrderCreateRQ", flowId = "flow_booking").size)
        assertEquals(0, service.listExamples("OrderCreateRQ", version = "25.4").size)
    }

    @Test
    fun `getExampleContent resolves ndc_content-prefixed xml_path`() {
        val root = tempDir()

        write(root.resolve("examples/files/iata/ex_2.xml"), "<IATA_AirShoppingRQ/>")

        val catalog = ExampleCatalog(
            version = 1,
            generatedAt = "2026-02-14T00:00:00Z",
            examples = listOf(
                ExampleRecord(
                    id = "ex_2",
                    source = "iata",
                    message = "IATA_AirShoppingRQ",
                    version = "21.3.5",
                    title = "sample",
                    fileName = "sample.xml",
                    xmlPath = "ndc_content/examples/files/iata/ex_2.xml",
                    publicPath = "/content/examples/files/iata/ex_2.xml",
                ),
            ),
        )
        write(root.resolve("examples/catalog.json"), json.encodeToString(ExampleCatalog.serializer(), catalog))

        val service = ExampleService(root)

        assertNotNull(service.getExampleById("ex_2"))
        assertEquals("<IATA_AirShoppingRQ/>", service.getExampleContent("ex_2"))
        assertNull(service.getExampleById("ex_missing"))
        assertNull(service.getExampleContent("ex_missing"))
    }
}
