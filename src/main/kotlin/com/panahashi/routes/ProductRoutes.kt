package com.panahashi.routes

import com.panahashi.config.getBakeryId
import com.panahashi.config.requireAdmin
import com.panahashi.models.ApiResponse
import com.panahashi.models.CreateProductRequest
import com.panahashi.models.UpdateProductRequest
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

        // ── RUTAS PÚBLICAS ────────────────────────────────────────────

        // GET /api/v1/products?bakeryId=xxx — productos disponibles de una panadería
        get {
            val bakeryId = call.request.queryParameters["bakeryId"]
                ?: throw IllegalArgumentException("bakeryId es requerido")
            val products = ProductService.getAvailableProductsByBakery(bakeryId)
            call.respond(ApiResponse(success = true, data = products))
        }



        authenticate("firebase-auth") {

            // ── BAKER ────────────────────────────────────────────────

            // GET /api/v1/products/my — TODOS mis productos
            get("my") {
                val bakeryId = call.getBakeryId()
                val products = ProductService.getAllProductsByBakery(bakeryId)
                call.respond(ApiResponse(success = true, data = products))
            }

            // POST /api/v1/products — crear producto
            post {
                val bakeryId = call.getBakeryId()
                val request  = call.receive<CreateProductRequest>()
                val product  = ProductService.createProduct(bakeryId, request)
                call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = product))
            }

            // POST /api/v1/products/seed — datos de prueba
            post("seed") {
                val bakeryId = call.getBakeryId()
                ProductService.seedInitialProducts(bakeryId)
                call.respond(ApiResponse<Unit>(success = true, message = "Productos cargados"))
            }

            // ── ADMIN ────────────────────────────────────────────────

            // GET /api/v1/products/admin/all — todos de todas las panaderías
            get("admin/all") {
                if (!call.requireAdmin()) return@get
                val products = ProductService.getAllProducts()
                call.respond(ApiResponse(success = true, data = products))
            }

            // GET /api/v1/products/{id} — detalle de un producto
            get("{id}") {
                val id      = call.parameters["id"] ?: throw IllegalArgumentException("id requerido")
                val product = ProductService.getProductById(id)
                call.respond(ApiResponse(success = true, data = product))
            }

            // NUEVO: PATCH /api/v1/products/{id} — editar producto completo
            patch("{id}") {
                val bakeryId = call.getBakeryId()
                val id       = call.parameters["id"] ?: throw IllegalArgumentException("id requerido")
                ProductService.assertProductBelongsToBakery(id, bakeryId)
                val request = call.receive<UpdateProductRequest>()
                val product = ProductService.updateProduct(id, request)
                call.respond(ApiResponse(success = true, data = product))
            }

            // PATCH /api/v1/products/{id}/stock — solo actualizar stock
            patch("{id}/stock") {
                val bakeryId = call.getBakeryId()
                val id       = call.parameters["id"] ?: throw IllegalArgumentException("id requerido")
                ProductService.assertProductBelongsToBakery(id, bakeryId)
                val request = call.receive<UpdateStockRequest>()
                val product = ProductService.updateStock(id, request.stock)
                call.respond(ApiResponse(success = true, data = product))
            }

            // DELETE /api/v1/products/{id}
            delete("{id}") {
                val bakeryId = call.getBakeryId()
                val id       = call.parameters["id"] ?: throw IllegalArgumentException("id requerido")
                ProductService.assertProductBelongsToBakery(id, bakeryId)
                ProductService.deleteProduct(id)
                call.respond(ApiResponse<Unit>(success = true, message = "Producto eliminado"))
            }


        }
    }
}
