package com.panahashi.services

import com.panahashi.models.CreateReviewRequest
import com.panahashi.models.OrderStatus
import com.panahashi.models.Review
import com.panahashi.services.Collections.BAKERIES
import com.panahashi.services.Collections.ORDERS
import com.panahashi.services.Collections.REVIEWS

object ReviewService {

    // ─── Crear reseña ────────────────────────────────────────
    suspend fun createReview(userId: String, userName: String, request: CreateReviewRequest): Review {
        // Validar rating
        if (request.rating !in 1..5)
            throw IllegalArgumentException("El rating debe ser entre 1 y 5")

        // Validar que la orden existe y pertenece al usuario
        val orderDoc = FirestoreService.getDocument(ORDERS, request.orderId)
            ?: throw NoSuchElementException("Orden ${request.orderId} no encontrada")
        if (!orderDoc.exists())
            throw NoSuchElementException("Orden ${request.orderId} no encontrada")

        val orderUserId = orderDoc.getString("userId") ?: ""
        if (orderUserId != userId)
            throw IllegalArgumentException("Esta orden no te pertenece")

        val orderStatus = orderDoc.getString("status") ?: ""
        if (orderStatus != OrderStatus.COMPLETED.name)
            throw IllegalArgumentException("Solo puedes reseñar órdenes completadas. Estado actual: $orderStatus")

        val bakeryId = orderDoc.getString("bakeryId")
            ?: throw IllegalStateException("La orden no tiene panadería asociada")

        // Validar que el usuario no haya reseñado esta orden antes
        val existing = FirestoreService.queryCollectionMultiple(
            REVIEWS,
            mapOf("orderId" to request.orderId, "userId" to userId)
        )
        if (existing.isNotEmpty())
            throw IllegalArgumentException("Ya reseñaste esta orden")

        // Guardar la reseña
        val data = mapOf(
            "bakeryId"  to bakeryId,
            "userId"    to userId,
            "userName"  to userName,
            "orderId"   to request.orderId,
            "rating"    to request.rating,
            "comment"   to request.comment,
            "createdAt" to System.currentTimeMillis()
        )
        val reviewId = FirestoreService.createDocumentAutoId(REVIEWS, data)

        // Recalcular y actualizar rating de la panadería
        recalculateBakeryRating(bakeryId)

        return getReviewById(reviewId)
    }

    // ─── Obtener reseñas de una panadería ────────────────────
    suspend fun getReviewsByBakery(bakeryId: String, page: Int = 1, pageSize: Int = 20): List<Review> {
        val all = FirestoreService.queryCollection(REVIEWS, "bakeryId", bakeryId)
            .map { it.toReview() }
            .sortedByDescending { it.createdAt }

        val from = ((page - 1) * pageSize).coerceAtMost(all.size)
        val to   = (from + pageSize).coerceAtMost(all.size)
        return all.subList(from, to)
    }

    // ─── Obtener reseñas de un usuario ───────────────────────
    suspend fun getReviewsByUser(userId: String): List<Review> {
        return FirestoreService.queryCollection(REVIEWS, "userId", userId)
            .map { it.toReview() }
            .sortedByDescending { it.createdAt }
    }

    // ─── Verificar si ya se reseñó una orden ─────────────────
    suspend fun hasReviewForOrder(userId: String, orderId: String): Boolean {
        val results = FirestoreService.queryCollectionMultiple(
            REVIEWS,
            mapOf("orderId" to orderId, "userId" to userId)
        )
        return results.isNotEmpty()
    }

    // ─── Helpers internos ────────────────────────────────────

    private suspend fun getReviewById(id: String): Review {
        val doc = FirestoreService.getDocument(REVIEWS, id)
            ?: throw NoSuchElementException("Reseña $id no encontrada")
        return doc.toReview()
    }

    /**
     * Recalcula el rating promedio de la panadería basándose
     * en TODAS sus reseñas. Se llama cada vez que se crea una reseña.
     */
    private suspend fun recalculateBakeryRating(bakeryId: String) {
        val reviews = FirestoreService.queryCollection(REVIEWS, "bakeryId", bakeryId)
            .map { (it.getLong("rating") ?: 5L).toInt() }

        if (reviews.isEmpty()) return

        val newRating = reviews.average()
        val totalReviews = reviews.size

        FirestoreService.updateDocument(
            BAKERIES, bakeryId,
            mapOf(
                "rating"       to newRating,
                "totalReviews" to totalReviews
            )
        )
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toReview() = Review(
        id          = id,
        bakeryId    = getString("bakeryId")  ?: "",
        userId      = getString("userId")    ?: "",
        userName    = getString("userName")  ?: "",
        orderId     = getString("orderId")   ?: "",
        rating      = getLong("rating")?.toInt() ?: 5,
        comment     = getString("comment")   ?: "",
        createdAt   = getLong("createdAt")   ?: 0L
    )
}
