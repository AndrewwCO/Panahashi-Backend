package com.panahashi.routes

import com.panahashi.config.requireAdmin
import com.panahashi.models.ApiResponse
import com.panahashi.models.CreatePaymentRequest
import com.panahashi.services.PaymentService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.paymentRoutes() {
    route("/payments") {

        authenticate("firebase-auth") {

            // ── CLIENTE ───────────────────────────────────────────────

            // POST /api/v1/payments — crear/procesar pago (simulado)
            // Para tarjeta que termina en 0000 → simula rechazo
            // Para CASH_ON_PICKUP o cualquier otra tarjeta → aprobado
            post {
                val uid     = call.principal<UserIdPrincipal>()!!.name
                val request = call.receive<CreatePaymentRequest>()
                val payment = PaymentService.createPayment(uid, request)
                val status  = if (payment.status.name == "APPROVED") HttpStatusCode.OK
                              else HttpStatusCode.PaymentRequired
                call.respond(status, ApiResponse(
                    success = payment.status.name == "APPROVED",
                    data    = payment,
                    message = when (payment.status.name) {
                        "APPROVED" -> "Pago procesado exitosamente"
                        "REJECTED" -> "Pago rechazado. Verifica los datos de tu tarjeta."
                        else       -> null
                    }
                ))
            }

            // GET /api/v1/payments/order/{orderId} — consultar pago de una orden
            get("order/{orderId}") {
                val orderId = call.parameters["orderId"]
                    ?: throw IllegalArgumentException("orderId requerido")
                val payment = PaymentService.getPaymentByOrder(orderId)
                call.respond(ApiResponse(success = true, data = payment))
            }

            // ── ADMIN ─────────────────────────────────────────────────

            // POST /api/v1/payments/order/{orderId}/refund — reembolso (solo admin)
            post("order/{orderId}/refund") {
                if (!call.requireAdmin()) return@post
                val orderId = call.parameters["orderId"]
                    ?: throw IllegalArgumentException("orderId requerido")
                val payment = PaymentService.refundPayment(orderId)
                call.respond(ApiResponse(success = true, data = payment, message = "Reembolso procesado"))
            }
        }
    }
}
