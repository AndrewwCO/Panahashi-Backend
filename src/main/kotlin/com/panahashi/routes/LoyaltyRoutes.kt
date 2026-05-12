package com.panahashi.routes

import com.panahashi.config.getBakeryId
import com.panahashi.models.ApiResponse
import com.panahashi.services.LoyaltyService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.loyaltyRoutes() {
    route("/loyalty") {

        authenticate("firebase-auth") {

            // ── CLIENTE ───────────────────────────────────────────────

            // GET /api/v1/loyalty — todas mis tarjetas de fidelidad
            get {
                val uid   = call.principal<UserIdPrincipal>()!!.name
                val cards = LoyaltyService.getAllCardsForUser(uid)
                call.respond(ApiResponse(success = true, data = cards))
            }

            // GET /api/v1/loyalty/{bakeryId} — mi tarjeta en una panadería específica
            get("{bakeryId}") {
                val uid      = call.principal<UserIdPrincipal>()!!.name
                val bakeryId = call.parameters["bakeryId"]
                    ?: throw IllegalArgumentException("bakeryId requerido")
                val card = LoyaltyService.getCard(uid, bakeryId)
                call.respond(ApiResponse(success = true, data = card))
            }

            // ── BAKER ─────────────────────────────────────────────────

            // POST /api/v1/loyalty/redeem — baker redime una recompensa del cliente
            // Body: { "userId": "uid_del_cliente" }
            // Se usa después de que el baker escanea el QR de la tarjeta de fidelidad del cliente.
            post("redeem") {
                val bakeryId = call.getBakeryId()
                val body     = call.receive<Map<String, String>>()
                val userId   = body["userId"]
                    ?: throw IllegalArgumentException("userId requerido")
                val card = LoyaltyService.redeemReward(userId, bakeryId)
                call.respond(ApiResponse(
                    success = true,
                    data    = card,
                    message = "Recompensa canjeada exitosamente"
                ))
            }
        }
    }
}
