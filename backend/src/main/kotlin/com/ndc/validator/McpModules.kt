package com.ndc.validator

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.ConcurrentHashMap
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema

fun Route.configureMcpRoutes(schemaService: SchemaService, validatorService: ValidatorService) {
    val server = Server(
        Implementation(
            name = "ndc-validator",
            version = "0.1.0",
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            ),
        ),
    )

    server.addTool(
        name = "validate_ndc_xml",
        description = "Validates an NDC XML message against a specific schema version.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("version", buildJsonObject { put("type", "string"); put("description", "NDC Schema Version (e.g., 21.3)") })
                put("message", buildJsonObject { put("type", "string"); put("description", "Message Name (e.g., AirShoppingRQ)") })
                put("xml", buildJsonObject { put("type", "string"); put("description", "Raw XML content to validate") })
            },
            required = listOf("version", "message", "xml")
        )
    ) { request ->
        val args = request.arguments
        val version = (args?.get("version") as? JsonPrimitive)?.content ?: ""
        val message = (args?.get("message") as? JsonPrimitive)?.content ?: ""
        val xml = (args?.get("xml") as? JsonPrimitive)?.content ?: ""

        val result = validatorService.validate(version, message, xml)

        CallToolResult(
            content = listOf(
                TextContent(
                    text = if (result.valid) "Valid" else "Invalid: ${result.errors.joinToString(", ")}"
                )
            ),
            isError = !result.valid
        )
    }

    server.addTool(
        name = "list_versions",
        description = "Lists all available NDC schema versions.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {},
            required = emptyList()
        )
    ) { _ ->
        val schemas = schemaService.listSchemas()
        CallToolResult(content = listOf(TextContent(text = schemas.keys.sorted().joinToString("\n"))))
    }

    server.addTool(
        name = "list_schemas",
        description = "Lists available NDC messages. If version is provided, lists for that version. Otherwise lists all messages for all versions.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("version", buildJsonObject { put("type", "string"); put("description", "Optional: Specific version to list messages for (e.g., 21.3)") })
            },
            required = emptyList()
        )
    ) { request ->
        val args = request.arguments
        val version = (args?.get("version") as? JsonPrimitive)?.content

        val schemas = schemaService.listSchemas()
        
        val resultText = if (!version.isNullOrEmpty()) {
            val messages = schemas[version]
            if (messages != null) {
                messages.joinToString("\n")
            } else {
                "Version $version not found. Available versions: ${schemas.keys.sorted().joinToString(", ")}"
            }
        } else {
            val sb = StringBuilder()
            schemas.toSortedMap().forEach { (ver, msgs) ->
                sb.append("Version $ver:\n")
                msgs.forEach { sb.append("  - $it\n") }
                sb.append("\n")
            }
            sb.toString()
        }

        CallToolResult(content = listOf(TextContent(text = resultText)))
    }

    server.addTool(
        name = "get_schema_files",
        description = "Retrieves all XSD files for a specific message and version.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("version", buildJsonObject { put("type", "string"); put("description", "NDC Schema Version (e.g., 21.3)") })
                put("message", buildJsonObject { put("type", "string"); put("description", "Message Name (e.g., OfferPriceRS)") })
            },
            required = listOf("version", "message")
        )
    ) { request ->
        val args = request.arguments
        val version = (args?.get("version") as? JsonPrimitive)?.content ?: ""
        val message = (args?.get("message") as? JsonPrimitive)?.content ?: ""

        val files = schemaService.getSchemaFiles(version, message)

        if (files != null) {
            val contentList = files.map { file ->
                TextContent(
                    text = "// File: ${file.name}\n\n${file.readText()}"
                )
            }
            CallToolResult(content = contentList)
        } else {
            CallToolResult(
                content = listOf(TextContent(text = "Schema not found for version $version and message $message")),
                isError = true
            )
        }
    }

    val serverSessions = ConcurrentHashMap<String, ServerSession>()

    sse("/mcp/sse") {
        val transport = SseServerTransport("/mcp/messages", this)
        val serverSession = server.createSession(transport)
        serverSessions[transport.sessionId] = serverSession

        try {
            serverSession.onClose {
                serverSessions.remove(transport.sessionId)
            }
            awaitCancellation()
        } finally {
            serverSessions.remove(transport.sessionId)
        }
    }

    post("/mcp/messages") {
        val sessionId = call.request.queryParameters["sessionId"]
        if (sessionId == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing sessionId parameter")
            return@post
        }

        val session = serverSessions[sessionId]
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return@post
        }

        val transport = session.transport as? SseServerTransport
        if (transport == null) {
            call.respond(HttpStatusCode.InternalServerError, "Invalid session transport")
            return@post
        }

        transport.handlePostMessage(call)
    }
}
