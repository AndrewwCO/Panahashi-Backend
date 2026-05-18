package com.panahashi.routes

import com.panahashi.config.getBakeryId
import com.panahashi.config.requireAdmin
import com.panahashi.models.ApiResponse
import com.panahashi.services.BakeryService
import com.panahashi.services.ProductService
import com.panahashi.services.StorageService
import com.panahashi.models.UpdateProductRequest
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.uploadRoutes() {
    route("/upload") {

        authenticate("firebase-auth") {

            /**
             * POST /api/v1/upload/product/{productId}
             * Baker sube la foto de uno de sus productos.
             * Body: multipart/form-data con campo "image"
             */
            post("product/{productId}") {
                val bakeryId = call.getBakeryId()
                val productId = call.parameters["productId"]
                    ?: throw IllegalArgumentException("productId requerido")

                // Verificar que el producto es de esta panadería
                ProductService.assertProductBelongsToBakery(productId, bakeryId)

                val (bytes, contentType) = extractImageFromMultipart(call)

                // Si el producto ya tenía foto, eliminar la anterior
                val existing = ProductService.getProductById(productId)
                if (existing.imageUrl.isNotEmpty()) {
                    StorageService.deleteImage(existing.imageUrl)
                }

                val url = StorageService.uploadImage(bytes, contentType, "products/$bakeryId")

                // Guardar la URL en Firestore
                val updated = ProductService.updateProduct(productId, UpdateProductRequest(imageUrl = url))
                call.respond(ApiResponse(success = true, data = updated))
            }

            /**
             * POST /api/v1/upload/bakery/logo
             * Baker sube el logo de su panadería.
             */
            post("bakery/logo") {
                val bakeryId = call.getBakeryId()
                val (bytes, contentType) = extractImageFromMultipart(call)

                val existing = BakeryService.getBakeryById(bakeryId)
                if (existing.logoUrl.isNotEmpty()) {
                    StorageService.deleteImage(existing.logoUrl)
                }

                val url = StorageService.uploadImage(bytes, contentType, "bakeries/$bakeryId/logo")
                val updated = BakeryService.updateBakery(
                    bakeryId,
                    com.panahashi.models.UpdateBakeryRequest(logoUrl = url)
                )
                call.respond(ApiResponse(success = true, data = updated))
            }

            /**
             * POST /api/v1/upload/bakery/banner
             * Baker sube el banner de su panadería.
             */
            post("bakery/banner") {
                val bakeryId = call.getBakeryId()
                val (bytes, contentType) = extractImageFromMultipart(call)

                val existing = BakeryService.getBakeryById(bakeryId)
                if (existing.bannerUrl.isNotEmpty()) {
                    StorageService.deleteImage(existing.bannerUrl)
                }

                val url = StorageService.uploadImage(bytes, contentType, "bakeries/$bakeryId/banner")
                val updated = BakeryService.updateBakery(
                    bakeryId,
                    com.panahashi.models.UpdateBakeryRequest(bannerUrl = url)
                )
                call.respond(ApiResponse(success = true, data = updated))
            }

            /**
             * POST /api/v1/upload/bakery/{bakeryId}/logo
             * Admin sube el logo de cualquier panadería.
             */
            post("bakery/{bakeryId}/logo") {
                if (!call.requireAdmin()) return@post
                val bakeryId = call.parameters["bakeryId"]
                    ?: throw IllegalArgumentException("bakeryId requerido")
                val (bytes, contentType) = extractImageFromMultipart(call)

                val url = StorageService.uploadImage(bytes, contentType, "bakeries/$bakeryId/logo")
                val updated = BakeryService.updateBakery(
                    bakeryId,
                    com.panahashi.models.UpdateBakeryRequest(logoUrl = url)
                )
                call.respond(ApiResponse(success = true, data = updated))
            }
        }
    }
}

// ─── Helper: extrae imagen del multipart ──────────────────────
private suspend fun extractImageFromMultipart(call: ApplicationCall): Pair<ByteArray, String> {
    val multipart = call.receiveMultipart()
    var imageBytes: ByteArray? = null
    var contentType: String? = null

    multipart.forEachPart { part ->
        if (part is PartData.FileItem && part.name == "image") {
            contentType = part.contentType?.toString()
                ?: throw IllegalArgumentException("El archivo debe tener un Content-Type")
            imageBytes = part.streamProvider().readBytes()
        }
        part.dispose()
    }

    return Pair(
        imageBytes ?: throw IllegalArgumentException("No se encontró el campo 'image' en el multipart"),
        contentType ?: throw IllegalArgumentException("Content-Type de imagen requerido")
    )
}
