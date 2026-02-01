package com.ndc.validator

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class ValidationRequest(val version: String, val message: String, val xml: String)

fun Route.configureRoutes(schemaService: SchemaService, validatorService: ValidatorService) {
    get("/schemas") {
        call.respond(schemaService.listSchemas())
    }

    post("/validate") {
        val request = call.receive<ValidationRequest>()
        val result = validatorService.validate(request.version, request.message, request.xml)
        call.respond(result)
    }
}
