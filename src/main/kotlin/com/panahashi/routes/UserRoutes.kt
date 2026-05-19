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

            // PATCH /api/v1/users/me — actualizar mi perfil
            patch("me") {
                val uid = call.principal<UserIdPrincipal>()!!.name
                val request = call.receive<UpdateProfileRequest>()
                val profile = UserService.updateProfile(uid, request)
                call.respond(ApiResponse(success = true, data = profile))
            }

            // ── ADMIN ─────────────────────────────────────────────────

            // GET /api/v1/users — listar todos los usuarios (solo admin)
            get {
                if (!call.requireAdmin()) return@get
                val users = UserService.getAllUsers()
                call.respond(ApiResponse(success = true, data = users))
            }

            // GET /api/v1/users/bakers — listar solo usuarios con rol BAKER (solo admin)
            // Usado en el panel para elegir el owner al crear una panadería
            get("bakers") {
                if (!call.requireAdmin()) return@get
                val bakers = UserService.getBakers()
                call.respond(ApiResponse(success = true, data = bakers))
            }

            // GET /api/v1/users/{uid} — ver perfil de cualquier usuario (admin)
            get("{uid}") {
                if (!call.requireAdmin()) return@get
                val uid = call.parameters["uid"] ?: throw IllegalArgumentException("uid requerido")
                val profile = UserService.getUserById(uid)
                call.respond(ApiResponse(success = true, data = profile))
            }

            // PATCH /api/v1/users/{uid}/role — cambiar rol (solo admin)
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