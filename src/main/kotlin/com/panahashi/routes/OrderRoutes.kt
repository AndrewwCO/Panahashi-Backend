package com.panahashi.routes

import com.panahashi.models.ApiResponse
import com.panahashi.models.CreateOrderRequest
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

            // POST /api/v1/orders — crear nueva orden
            post {
                val uid = call.principal<UserIdPrincipal>()!!.name
                val request = call.receive<CreateOrderRequest>()

                // Obtener nombre del usuario para la orden
                val userName = call.request.headers["X-User-Name"] ?: "Cliente"

                val order = OrderService.createOrder(uid, userName, request)
                call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = order))
            }

            // GET /api/v1/orders/me — mis órdenes
            get("me") {
                val uid = call.principal<UserIdPrincipal>()!!.name
                val orders = OrderService.getOrdersByUser(uid)
                call.respond(ApiResponse(success = true, data = orders))
            }

            // GET /api/v1/orders/{id} — orden por id
            get("{id}") {
                val uid = call.principal<UserIdPrincipal>()!!.name
                val orderId = call.parameters["id"] ?: throw IllegalArgumentException("id requerido")
                val order = OrderService.getOrderById(orderId)

                // Solo el dueño puede ver su orden (o admin)
                if (order.userId != uid) {
                    call.respond(HttpStatusCode.Forbidden, ApiResponse<Unit>(success = false, message = "Acceso denegado"))
                    return@get
                }
                call.respond(ApiResponse(success = true, data = order))
            }

            // PATCH /api/v1/orders/{id}/status — cambiar estado (admin/baker)
            patch("{id}/status") {
                val orderId = call.parameters["id"] ?: throw IllegalArgumentException("id requerido")
                val request = call.receive<UpdateOrderStatusRequest>()
                val order = OrderService.updateOrderStatus(orderId, request.status)
                call.respond(ApiResponse(success = true, data = order))
            }

            // GET /api/v1/orders/all — todas las órdenes (admin)
            get("all") {
                val orders = OrderService.getAllOrders()
                call.respond(ApiResponse(success = true, data = orders))
            }
        }
    }
}
