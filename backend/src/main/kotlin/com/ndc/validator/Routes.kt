package com.ndc.validator

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class ValidationRequest(val version: String, val message: String, val xml: String)

fun Route.configureRoutes(schemaService: SchemaService, validatorService: ValidatorService, schemaDiffService: SchemaDiffService) {
    get("/") {
        call.respondRedirect("https://ndc-clearance.netlify.app", permanent = false)
    }

    get("/schemas") {
        call.respond(schemaService.listSchemas())
    }

    post("/validate") {
        val request = call.receive<ValidationRequest>()
        val result = validatorService.validate(request.version, request.message, request.xml)
        call.respond(result)
    }

    get("/api/diff") {
        val from = call.request.queryParameters["from"]
        val to = call.request.queryParameters["to"]
        
        if (from == null || to == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing 'from' or 'to' query parameters")
            return@get
        }
        
        try {
            val diff = schemaDiffService.compareVersions(from, to)
            call.respond(diff)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Error generating diff: ${e.message}")
        }
    }
}
