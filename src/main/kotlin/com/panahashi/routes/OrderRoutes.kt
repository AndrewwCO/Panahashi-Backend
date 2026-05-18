package com.panahashi.routes

import com.panahashi.config.ApiError
import com.panahashi.config.getBakeryId
import com.panahashi.config.requireAdmin
import com.panahashi.models.ApiResponse
import com.panahashi.models.CreateOrderRequest
import com.panahashi.models.SetEstimatedReadyRequest
import com.panahashi.models.UpdateOrderStatusRequest
import com.panahashi.services.OrderService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.orderRoutes() {
    route("/orders") {

        authenticate("firebase-auth") {

            // ── CLIENTE ───────────────────────────────────────────────

            // POST /api/v1/orders — crear orden
            post {
                val uid      = call.principal<UserIdPrincipal>()!!.name
                val userName = call.request.headers["X-User-Name"] ?: "Cliente"
                val request  = call.receive<CreateOrderRequest>()
                val order    = OrderService.createOrder(uid, userName, request)
                call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = order))
            }

            // GET /api/v1/orders/me?page=1&pageSize=20 — mis órdenes (cliente)
            get("me") {
                val uid      = call.principal<UserIdPrincipal>()!!.name
                val page     = call.request.queryParameters["page"]?.toIntOrNull()     ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
                val orders   = OrderService.getOrdersByUser(uid, page, pageSize)
                call.respond(ApiResponse(success = true, data = orders))
            }



            // ── BAKER ─────────────────────────────────────────────────

            // POST /api/v1/orders/verify-qr — baker escanea QR del cliente
            post("verify-qr") {
                val bakeryId = call.getBakeryId()
                val body     = call.receive<Map<String, String>>()
                val qrCode   = body["qrCode"] ?: throw IllegalArgumentException("qrCode requerido")
                val order    = OrderService.verifyQr(qrCode, bakeryId)
                call.respond(ApiResponse(success = true, data = order))
            }

            // GET /api/v1/orders/bakery?page=1&pageSize=20 — todas las órdenes de mi panadería
            get("bakery") {
                val bakeryId = call.getBakeryId()
                val page     = call.request.queryParameters["page"]?.toIntOrNull()     ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
                val orders   = OrderService.getOrdersByBakery(bakeryId, page, pageSize)
                call.respond(ApiResponse(success = true, data = orders))
            }

            // GET /api/v1/orders/bakery/pending — órdenes PENDIENTES
            get("bakery/pending") {
                val bakeryId = call.getBakeryId()
                val orders   = OrderService.getPendingOrdersByBakery(bakeryId)
                call.respond(ApiResponse(success = true, data = orders))
            }

            // NUEVO: GET /api/v1/orders/bakery/active — órdenes activas (PENDING+CONFIRMED+BAKING)
            // Útil para el panel en tiempo real del baker
            get("bakery/active") {
                val bakeryId = call.getBakeryId()
                val orders   = OrderService.getActiveOrdersByBakery(bakeryId)
                call.respond(ApiResponse(success = true, data = orders))
            }

            // GET /api/v1/orders/{id} — orden por id (el cliente solo ve la suya)
            get("{id}") {
                val uid     = call.principal<UserIdPrincipal>()!!.name
                val orderId = call.parameters["id"] ?: throw IllegalArgumentException("id requerido")
                val order   = OrderService.getOrderById(orderId)

                if (order.userId != uid) {
                    call.respond(HttpStatusCode.Forbidden, ApiError("FORBIDDEN", "Acceso denegado"))
                    return@get
                }
                call.respond(ApiResponse(success = true, data = order))
            }

            // PATCH /api/v1/orders/{id}/status — cambiar estado
            patch("{id}/status") {
                val bakeryId = call.getBakeryId()
                val orderId  = call.parameters["id"] ?: throw IllegalArgumentException("id requerido")
                val request  = call.receive<UpdateOrderStatusRequest>()
                val order    = OrderService.updateOrderStatus(orderId, request.status, bakeryId)
                call.respond(ApiResponse(success = true, data = order))
            }

            // PATCH /api/v1/orders/{id}/estimated-ready — baker fija el tiempo estimado
            patch("{id}/estimated-ready") {
                val bakeryId = call.getBakeryId()
                val orderId  = call.parameters["id"] ?: throw IllegalArgumentException("id requerido")
                val request  = call.receive<SetEstimatedReadyRequest>()
                val order    = OrderService.setEstimatedReady(orderId, request.estimatedReadyAt, bakeryId)
                call.respond(ApiResponse(success = true, data = order))
            }



            // ── ADMIN ─────────────────────────────────────────────────

            // GET /api/v1/orders/all?page=1&pageSize=50
            get("all") {
                if (!call.requireAdmin()) return@get
                val page     = call.request.queryParameters["page"]?.toIntOrNull()     ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 50
                val orders   = OrderService.getAllOrders(page, pageSize)
                call.respond(ApiResponse(success = true, data = orders))
            }
        }
    }
}
