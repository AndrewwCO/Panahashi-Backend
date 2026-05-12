package com.panahashi.routes

import com.panahashi.config.getBakeryId
import com.panahashi.models.ApiResponse
import com.panahashi.models.CreatePromotionRequest
import com.panahashi.services.PromotionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.promotionRoutes() {
    route("/promotions") {

        // ── PÚBLICAS ─────────────────────────────────────────────

        // GET /api/v1/promotions?bakeryId=xxx — promos activas de una panadería (clientes)
        get {
            val bakeryId = call.request.queryParameters["bakeryId"]
                ?: throw IllegalArgumentException("bakeryId requerido")
            val promos = PromotionService.getActivePromotionsByBakery(bakeryId)
            call.respond(ApiResponse(success = true, data = promos))
        }

        authenticate("firebase-auth") {

            // ── BAKER ─────────────────────────────────────────────────

            // GET /api/v1/promotions/me — todas mis promos (incluye inactivas)
            get("me") {
                val bakeryId = call.getBakeryId()
                val promos   = PromotionService.getAllPromotionsByBakery(bakeryId)
                call.respond(ApiResponse(success = true, data = promos))
            }

            // POST /api/v1/promotions — crear promoción
            post {
                val bakeryId = call.getBakeryId()
                val request  = call.receive<CreatePromotionRequest>()
                val promo    = PromotionService.createPromotion(bakeryId, request)
                call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = promo))
            }

            // PATCH /api/v1/promotions/{id}/toggle — activar/desactivar promoción
            patch("{id}/toggle") {
                val bakeryId    = call.getBakeryId()
                val promotionId = call.parameters["id"]
                    ?: throw IllegalArgumentException("id requerido")
                val promo = PromotionService.togglePromotion(promotionId, bakeryId)
                call.respond(ApiResponse(success = true, data = promo))
            }

            // DELETE /api/v1/promotions/{id} — eliminar promoción
            delete("{id}") {
                val bakeryId    = call.getBakeryId()
                val promotionId = call.parameters["id"]
                    ?: throw IllegalArgumentException("id requerido")
                PromotionService.deletePromotion(promotionId, bakeryId)
                call.respond(ApiResponse<Unit>(success = true, message = "Promoción eliminada"))
            }
        }
    }
}
