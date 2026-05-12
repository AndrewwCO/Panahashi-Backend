package com.panahashi.routes

import com.panahashi.config.requireAdmin
import com.panahashi.models.ApiResponse
import com.panahashi.models.UpdateProfileRequest
import com.panahashi.models.UserRole
import com.panahashi.services.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoutes() {
    route("/users") {

        authenticate("firebase-auth") {

            // GET /api/v1/users/me — obtener mi perfil
            get("me") {
                val uid = call.principal<UserIdPrincipal>()!!.name
                val profile = UserService.getOrCreateProfile(uid)
                call.respond(ApiResponse(success = true, data = profile))
            }

            // PATCH /api/v1/users/me — actualizar mi perfil (nombre, teléfono, fcmToken)
            patch("me") {
                val uid = call.principal<UserIdPrincipal>()!!.name
                val request = call.receive<UpdateProfileRequest>()
                val profile = UserService.updateProfile(uid, request)
                call.respond(ApiResponse(success = true, data = profile))
            }

            // ── ADMIN ─────────────────────────────────────────────────

            // GET /api/v1/users/{uid} — ver perfil de cualquier usuario (admin)
            get("{uid}") {
                if (!call.requireAdmin()) return@get
                val uid = call.parameters["uid"] ?: throw IllegalArgumentException("uid requerido")
                val profile = UserService.getUserById(uid)
                call.respond(ApiResponse(success = true, data = profile))
            }

            // PATCH /api/v1/users/{uid}/role — cambiar rol de un usuario (solo admin)
            // Uso: primero crea la cuenta del baker, luego llama este endpoint con role=BAKER,
            // luego crea la panadería con su ownerId. El orden importa.
            patch("{uid}/role") {
                if (!call.requireAdmin()) return@patch
                val uid = call.parameters["uid"] ?: throw IllegalArgumentException("uid requerido")
                val body = call.receive<Map<String, String>>()
                val roleStr = body["role"] ?: throw IllegalArgumentException("role requerido")
                val role = runCatching { UserRole.valueOf(roleStr) }
                    .getOrElse { throw IllegalArgumentException("Rol inválido. Valores válidos: ${UserRole.values().joinToString()}") }
                val profile = UserService.updateRole(uid, role)
                call.respond(ApiResponse(success = true, data = profile))
            }
        }
    }
}