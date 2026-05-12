package com.panahashi.routes

import com.panahashi.models.AddToCartRequest
import com.panahashi.models.ApiResponse
import com.panahashi.models.UpdateCartItemRequest
import com.panahashi.services.CartService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.cartRoutes() {
    route("/cart") {

        authenticate("firebase-auth") {

            // GET /api/v1/cart — obtener mi carrito actual
            get {
                val uid  = call.principal<UserIdPrincipal>()!!.name
                val cart = CartService.getCart(uid)
                call.respond(ApiResponse(success = true, data = cart))
            }

            // POST /api/v1/cart/items — agregar producto al carrito
            // Si el carrito tiene productos de otra panadería, se limpia automáticamente.
            post("items") {
                val uid     = call.principal<UserIdPrincipal>()!!.name
                val request = call.receive<AddToCartRequest>()
                val cart    = CartService.addToCart(uid, request)
                call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = cart))
            }

            // PATCH /api/v1/cart/items — actualizar cantidad de un item (qty=0 lo elimina)
            patch("items") {
                val uid     = call.principal<UserIdPrincipal>()!!.name
                val request = call.receive<UpdateCartItemRequest>()
                val cart    = CartService.updateCartItem(uid, request)
                call.respond(ApiResponse(success = true, data = cart))
            }

            // DELETE /api/v1/cart — vaciar carrito
            delete {
                val uid = call.principal<UserIdPrincipal>()!!.name
                CartService.clearCart(uid)
                call.respond(ApiResponse<Unit>(success = true, message = "Carrito vaciado"))
            }
        }
    }
}
