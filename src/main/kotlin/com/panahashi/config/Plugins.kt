package com.panahashi.config

import com.google.firebase.auth.FirebaseAuth
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun Application.configurePlugins() {

    // ─── JSON ───────────────────────────────────────────────
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // ─── CORS ────────────────────────────────────────────────
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-User-Name")
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
    }

    // ─── LOGGING ─────────────────────────────────────────────
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
    }

    // ─── FIREBASE AUTH ───────────────────────────────────────
    install(Authentication) {
        bearer("firebase-auth") {
            authenticate { credential ->
                runCatching {
                    val token = FirebaseAuth.getInstance().verifyIdToken(credential.token)
                    UserIdPrincipal(token.uid)
                }.getOrNull()
            }
        }
    }

    // ─── ERROR HANDLING ──────────────────────────────────────
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiError("BAD_REQUEST", cause.message ?: "Parámetros inválidos"))
        }
        exception<NoSuchElementException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ApiError("NOT_FOUND", cause.message ?: "Recurso no encontrado"))
        }
        exception<RequestValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiError("VALIDATION_ERROR", cause.reasons.joinToString()))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Error no manejado", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiError("INTERNAL_ERROR", "Error interno del servidor"))
        }
    }
}

@kotlinx.serialization.Serializable
data class ApiError(val code: String, val message: String)
