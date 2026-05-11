package com.panahashi.routes

import com.panahashi.models.ApiResponse
import com.panahashi.models.CreateProductRequest
import com.panahashi.models.UpdateStockRequest
import com.panahashi.services.ProductService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.productRoutes() {
    route("/products") {

        // GET /api/v1/products — listar todos los productos disponibles (público)
        get {
            val products = ProductService.getAvailableProducts()
            call.respond(ApiResponse(success = true, data = products))
        }

        // GET /api/v1/products/{id}
        get("{id}") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("id requerido")
            val product = ProductService.getProductById(id)
            call.respond(ApiResponse(success = true, data = product))
        }

        // Rutas protegidas (requieren autenticación Firebase)
        authenticate("firebase-auth") {

            // POST /api/v1/products — crear producto (admin)
            post {
                val request = call.receive<CreateProductRequest>()
                val product = ProductService.createProduct(request)
                call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = product))
            }

            // PATCH /api/v1/products/{id}/stock — actualizar stock
            patch("{id}/stock") {
                val id = call.parameters["id"] ?: throw IllegalArgumentException("id requerido")
                val request = call.receive<UpdateStockRequest>()
                val product = ProductService.updateStock(id, request.stock)
                call.respond(ApiResponse(success = true, data = product))
            }

            // DELETE /api/v1/products/{id}
            delete("{id}") {
                val id = call.parameters["id"] ?: throw IllegalArgumentException("id requerido")
                ProductService.deleteProduct(id)
                call.respond(ApiResponse<Unit>(success = true, message = "Producto eliminado"))
            }

            // POST /api/v1/products/seed — cargar productos iniciales (admin)
            post("seed") {
                ProductService.seedInitialProducts()
                call.respond(ApiResponse<Unit>(success = true, message = "Productos cargados"))
            }
        }
    }
}
