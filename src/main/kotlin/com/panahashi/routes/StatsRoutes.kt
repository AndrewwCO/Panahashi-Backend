package com.panahashi.routes

import com.panahashi.config.getBakeryId
import com.panahashi.config.requireAdmin
import com.panahashi.models.ApiResponse
import com.panahashi.services.StatsService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.statsRoutes() {
    route("/stats") {

        authenticate("firebase-auth") {

            // ── BAKER ─────────────────────────────────────────────────

            // GET /api/v1/stats/bakery — estadísticas de mi panadería
            // Incluye: ventas totales, ventas hoy, ventas semana, productos top, horas pico
            get("bakery") {
                val bakeryId = call.getBakeryId()
                val stats    = StatsService.getBakeryStats(bakeryId)
                call.respond(ApiResponse(success = true, data = stats))
            }

            // ── ADMIN ─────────────────────────────────────────────────

            // GET /api/v1/stats/admin — estadísticas globales de la plataforma
            get("admin") {
                if (!call.requireAdmin()) return@get
                val stats = StatsService.getAdminStats()
                call.respond(ApiResponse(success = true, data = stats))
            }
        }
    }
}
