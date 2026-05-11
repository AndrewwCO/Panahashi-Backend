package com.panahashi.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Route.healthRoutes() {
    get("/health") {
        call.respond(HealthResponse(status = "ok", service = "panahashi-backend", version = "1.0.0"))
    }


}

@Serializable
data class HealthResponse(val status: String, val service: String, val version: String)
