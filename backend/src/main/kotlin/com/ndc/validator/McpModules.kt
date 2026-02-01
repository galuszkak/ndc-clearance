package com.ndc.validator

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID

// Basic MCP Protocol Types
@Serializable
data class JsonRpcRequest(val jsonrpc: String = "2.0", val method: String, val params: JsonObject? = null, val id: Int? = null)

@Serializable
data class JsonRpcResponse(val jsonrpc: String = "2.0", val result: JsonElement? = null, val error: JsonRpcError? = null, val id: Int? = null)

@Serializable
data class JsonRpcError(val code: Int, val message: String, val data: JsonElement? = null)

// Tool Definition Structure
@Serializable
data class ToolDefinition(val name: String, val description: String, val inputSchema: JsonObject)

fun Route.configureMcpRoutes(validatorService: ValidatorService) {
    val tools = listOf(
        ToolDefinition(
            name = "validate_ndc_xml",
            description = "Validates an NDC XML message against a specific schema version.",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("version", buildJsonObject { put("type", "string"); put("description", "NDC Schema Version (e.g., 21.3)") })
                    put("message", buildJsonObject { put("type", "string"); put("description", "Message Name (e.g., AirShoppingRQ)") })
                    put("xml", buildJsonObject { put("type", "string"); put("description", "Raw XML content to validate") })
                })
                put("required", buildJsonArray { add("version"); add("message"); add("xml") })
            }
        )
    )

    // MCP SSE Endpoint
    get("/mcp/sse") {
        // SSE implementation requires persistent connection handling, simplify for now by just returning endpoint info
        // Typically, MCP over HTTP SSE involves:
        // 1. Client connects to /sse -> receives events
        // 2. Client posts messages to /messages -> processed, responses sent via SSE
        call.respondText("See /mcp/messages for POST interactions. This endpoint is a placeholder for SSE subscription.", ContentType.Text.EventStream)
    }

    // Direct JSON-RPC Endpoint (simpler than full SSE for basic tool use)
    post("/mcp/messages") {
        val request = call.receive<JsonRpcRequest>()
        
        when (request.method) {
            "tools/list" -> {
                val response = JsonRpcResponse(
                    id = request.id,
                    result = buildJsonObject {
                        put("tools", Json.encodeToJsonElement(tools))
                    }
                )
                call.respond(response)
            }
            "tools/call" -> {
                val params = request.params ?: throw IllegalArgumentException("Missing params")
                val name = params["name"]?.jsonPrimitive?.content
                val args = params["arguments"]?.jsonObject

                if (name == "validate_ndc_xml" && args != null) {
                    val version = args["version"]?.jsonPrimitive?.content ?: ""
                    val message = args["message"]?.jsonPrimitive?.content ?: ""
                    val xml = args["xml"]?.jsonPrimitive?.content ?: ""

                    val validationResult = validatorService.validate(version, message, xml)
                    
                    val toolResult = buildJsonObject {
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "text")
                                put("text", if (validationResult.valid) "Valid" else "Invalid: ${validationResult.errors.joinToString("\n")}")
                            })
                        })
                        put("isError", !validationResult.valid)
                    }

                    call.respond(JsonRpcResponse(id = request.id, result = toolResult))
                } else {
                    call.respond(JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Method not found or invalid params")))
                }
            }
            else -> {
                call.respond(JsonRpcResponse(id = request.id, error = JsonRpcError(-32601, "Method not supported")))
            }
        }
    }
}
