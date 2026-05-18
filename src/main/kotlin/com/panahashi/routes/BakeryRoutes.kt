package com.panahashi.routes

import com.panahashi.config.ApiError
import com.panahashi.config.getBakeryId
import com.panahashi.config.requireAdmin
import com.panahashi.models.ApiResponse
import com.panahashi.models.CreateBakeryRequest
import com.panahashi.models.UpdateBakeryRequest
import com.panahashi.services.BakeryService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.bakeryRoutes() {
    route("/bakeries") {

        // ── RUTAS PÚBLICAS ────────────────────────────────────────

        // GET /api/v1/bakeries — todas las panaderías activas
        get {
            val bakeries = BakeryService.getActiveBakeries()
            call.respond(ApiResponse(success = true, data = bakeries))
        }

        get("nearby") {
            val lat    = call.request.queryParameters["lat"]?.toDoubleOrNull()
                ?: throw IllegalArgumentException("lat requerido")
            val lng    = call.request.queryParameters["lng"]?.toDoubleOrNull()
                ?: throw IllegalArgumentException("lng requerido")
            val radius = call.request.queryParameters["radius"]?.toDoubleOrNull() ?: 5.0

            val results = BakeryService.getNearbyBakeries(lat, lng, radius)
            call.respond(ApiResponse(success = true, data = results))
        }

        // GET /api/v1/bakeries/all — todas incluyendo inactivas (solo admin)
        get("all") {
            if (!call.requireAdmin()) return@get
            val bakeries = BakeryService.getAllBakeries()
            call.respond(ApiResponse(success = true, data = bakeries))

            // FIX: corregido el typo "getNearbBakeries" → "getNearbyBakeries"
            // GET /api/v1/bakeries/nearby?lat=x&lng=y&radius=5
        }

        authenticate("firebase-auth") {

            // ── ADMIN ─────────────────────────────────────────────────

            // POST /api/v1/bakeries — crear panadería (solo admin)
            post {
                if (!call.requireAdmin()) return@post
                val request = call.receive<CreateBakeryRequest>()
                val bakery  = BakeryService.createBakery(request)
                call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = bakery))
            }

            // ── BAKER ────────────────────────────────────────────────

            // GET /api/v1/bakeries/me
            get("me") {
                val bakeryId = call.getBakeryId()
                val bakery   = BakeryService.getBakeryById(bakeryId)
                call.respond(ApiResponse(success = true, data = bakery))
            }

            // PATCH /api/v1/bakeries/me
            patch("me") {
                val bakeryId = call.getBakeryId()
                val request  = call.receive<UpdateBakeryRequest>()
                // Baker no puede cambiar status ni ownerId
                val safeRequest = request.copy(status = null)
                val bakery   = BakeryService.updateBakery(bakeryId, safeRequest)
                call.respond(ApiResponse(success = true, data = bakery))
            }

            // PATCH /api/v1/bakeries/me/open — abrir/cerrar panadería
            // FIX: ahora valida horario antes de abrir
            patch("me/open") {
                val bakeryId = call.getBakeryId()
                val body     = call.receive<Map<String, Boolean>>()
                val isOpen   = body["isOpen"] ?: throw IllegalArgumentException("isOpen requerido")
                val bakery   = BakeryService.toggleOpen(bakeryId, isOpen)
                call.respond(ApiResponse(success = true, data = bakery))
            }

            // DELETE /api/v1/bakeries/{id} — eliminar panadería (solo admin)
            delete("{id}") {
                if (!call.requireAdmin()) return@delete
                val id = call.parameters["id"] ?: throw IllegalArgumentException("id requerido")
                BakeryService.deleteBakery(id)
                call.respond(ApiResponse<Unit>(success = true, message = "Panadería eliminada"))
            }

            // PATCH /api/v1/bakeries/{id} — editar cualquier panadería (solo admin)
            patch("{id}") {
                if (!call.requireAdmin()) return@patch
                val id      = call.parameters["id"] ?: throw IllegalArgumentException("id requerido")
                val request = call.receive<UpdateBakeryRequest>()
                val bakery  = BakeryService.updateBakery(id, request)
                call.respond(ApiResponse(success = true, data = bakery))
            }
            // GET /api/v1/bakeries/{id} — detalle de una panadería
            get("{id}") {
                val id = call.parameters["id"] ?: throw IllegalArgumentException("id requerido")
                val bakery = BakeryService.getBakeryById(id)
                call.respond(ApiResponse(success = true, data = bakery))
            }


        }
    }
}
