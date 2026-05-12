package com.panahashi.routes

import com.panahashi.models.ApiResponse
import com.panahashi.models.CreateReviewRequest
import com.panahashi.services.ReviewService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.reviewRoutes() {
    route("/reviews") {

        // ── RUTAS PÚBLICAS ─────────────────────────────────────────

        // GET /api/v1/reviews?bakeryId=xxx&page=1&pageSize=20
        // Reseñas públicas de una panadería (clientes las ven sin login)
        get {
            val bakeryId = call.request.queryParameters["bakeryId"]
                ?: throw IllegalArgumentException("bakeryId es requerido")
            val page     = call.request.queryParameters["page"]?.toIntOrNull()     ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

            val reviews = ReviewService.getReviewsByBakery(bakeryId, page, pageSize)
            call.respond(ApiResponse(success = true, data = reviews))
        }

        authenticate("firebase-auth") {

            // ── CLIENTE ────────────────────────────────────────────────

            // POST /api/v1/reviews — crear reseña (solo después de orden COMPLETED)
            post {
                val uid      = call.principal<UserIdPrincipal>()!!.name
                val userName = call.request.headers["X-User-Name"] ?: "Cliente"
                val request  = call.receive<CreateReviewRequest>()

                val review = ReviewService.createReview(uid, userName, request)
                call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = review))
            }

            // GET /api/v1/reviews/me — mis reseñas
            get("me") {
                val uid = call.principal<UserIdPrincipal>()!!.name
                val reviews = ReviewService.getReviewsByUser(uid)
                call.respond(ApiResponse(success = true, data = reviews))
            }

            // GET /api/v1/reviews/can-review/{orderId}
            // El cliente puede preguntar si ya reseñó una orden antes de mostrar el formulario
            get("can-review/{orderId}") {
                val uid     = call.principal<UserIdPrincipal>()!!.name
                val orderId = call.parameters["orderId"]
                    ?: throw IllegalArgumentException("orderId requerido")
                val alreadyReviewed = ReviewService.hasReviewForOrder(uid, orderId)
                call.respond(ApiResponse(success = true, data = mapOf("canReview" to !alreadyReviewed)))
            }
        }
    }
}
