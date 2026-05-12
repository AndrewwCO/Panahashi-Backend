package com.panahashi.services

import com.panahashi.models.*
import com.panahashi.services.Collections.PROMOTIONS
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object PromotionService {

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun getActivePromotionsByBakery(bakeryId: String): List<Promotion> {
        return FirestoreService.queryCollectionMultiple(
            PROMOTIONS, mapOf("bakeryId" to bakeryId, "active" to true)
        ).map { it.toPromotion() }
    }

    suspend fun getAllPromotionsByBakery(bakeryId: String): List<Promotion> {
        return FirestoreService.queryCollection(PROMOTIONS, "bakeryId", bakeryId)
            .map { it.toPromotion() }
            .sortedByDescending { it.createdAt }
    }

    suspend fun getPromotionById(id: String): Promotion {
        val doc = FirestoreService.getDocument(PROMOTIONS, id)
            ?: throw NoSuchElementException("Promoción $id no encontrada")
        if (!doc.exists()) throw NoSuchElementException("Promoción $id no encontrada")
        return doc.toPromotion()
    }

    suspend fun createPromotion(bakeryId: String, request: CreatePromotionRequest): Promotion {
        validatePromotion(request)

        val data = mapOf(
            "bakeryId"         to bakeryId,
            "productId"        to request.productId,
            "title"            to request.title,
            "description"      to request.description,
            "type"             to request.type.name,
            "discountPct"      to request.discountPct,
            "discountAmount"   to request.discountAmount,
            "happyHourStart"   to request.happyHourStart,
            "happyHourEnd"     to request.happyHourEnd,
            "active"           to true,
            "createdAt"        to System.currentTimeMillis()
        )
        val id = FirestoreService.createDocumentAutoId(PROMOTIONS, data)
        return getPromotionById(id)
    }

    suspend fun togglePromotion(promotionId: String, bakeryId: String): Promotion {
        val promo = getPromotionById(promotionId)
        if (promo.bakeryId != bakeryId)
            throw IllegalArgumentException("Esta promoción no pertenece a tu panadería")
        FirestoreService.updateDocument(PROMOTIONS, promotionId, mapOf("active" to !promo.active))
        return getPromotionById(promotionId)
    }

    suspend fun deletePromotion(promotionId: String, bakeryId: String) {
        val promo = getPromotionById(promotionId)
        if (promo.bakeryId != bakeryId)
            throw IllegalArgumentException("Esta promoción no pertenece a tu panadería")
        FirestoreService.deleteDocument(PROMOTIONS, promotionId)
    }

    /**
     * Calcula el descuento que aplica a una orden dado un promotionId.
     * Retorna el monto de descuento (0.0 si no aplica).
     */
    suspend fun calculateDiscount(promotionId: String, items: List<CartItem>): Double {
        if (promotionId.isEmpty()) return 0.0

        val promo = runCatching { getPromotionById(promotionId) }.getOrNull() ?: return 0.0
        if (!promo.active) return 0.0

        val now = LocalTime.now()

        return when (promo.type) {
            PromotionType.HAPPY_HOUR -> {
                val start = runCatching { LocalTime.parse(promo.happyHourStart, timeFmt) }.getOrNull() ?: return 0.0
                val end   = runCatching { LocalTime.parse(promo.happyHourEnd,   timeFmt) }.getOrNull() ?: return 0.0
                if (now.isBefore(start) || now.isAfter(end)) return 0.0
                // Aplica porcentaje sobre los items relevantes
                applyPercentage(promo, items)
            }
            PromotionType.PERCENTAGE -> applyPercentage(promo, items)
            PromotionType.FIXED_AMOUNT -> {
                val base = getBase(promo, items)
                minOf(promo.discountAmount, base)   // No puede ser mayor al total
            }
        }
    }

    // ─── Helpers privados ─────────────────────────────────────

    private fun applyPercentage(promo: Promotion, items: List<CartItem>): Double {
        val base = getBase(promo, items)
        return (base * promo.discountPct / 100.0)
    }

    private fun getBase(promo: Promotion, items: List<CartItem>): Double {
        return if (promo.productId.isEmpty()) {
            // Aplica a toda la orden
            items.sumOf { it.price * it.qty }
        } else {
            // Aplica solo a ese producto
            items.filter { it.productId == promo.productId }.sumOf { it.price * it.qty }
        }
    }

    private fun validatePromotion(req: CreatePromotionRequest) {
        if (req.title.isBlank()) throw IllegalArgumentException("El título es requerido")
        when (req.type) {
            PromotionType.PERCENTAGE -> {
                if (req.discountPct <= 0 || req.discountPct > 100)
                    throw IllegalArgumentException("El porcentaje de descuento debe estar entre 1 y 100")
            }
            PromotionType.FIXED_AMOUNT -> {
                if (req.discountAmount <= 0)
                    throw IllegalArgumentException("El monto de descuento debe ser mayor a 0")
            }
            PromotionType.HAPPY_HOUR -> {
                if (req.happyHourStart.isEmpty() || req.happyHourEnd.isEmpty())
                    throw IllegalArgumentException("Las horas de happy hour son requeridas")
                if (req.discountPct <= 0 || req.discountPct > 100)
                    throw IllegalArgumentException("El porcentaje de descuento debe estar entre 1 y 100")
            }
        }
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toPromotion() = Promotion(
        id             = id,
        bakeryId       = getString("bakeryId")       ?: "",
        productId      = getString("productId")      ?: "",
        title          = getString("title")          ?: "",
        description    = getString("description")    ?: "",
        type           = PromotionType.valueOf(getString("type") ?: "PERCENTAGE"),
        discountPct    = getDouble("discountPct")    ?: 0.0,
        discountAmount = getDouble("discountAmount") ?: 0.0,
        happyHourStart = getString("happyHourStart") ?: "",
        happyHourEnd   = getString("happyHourEnd")   ?: "",
        active         = getBoolean("active")        ?: true,
        createdAt      = getLong("createdAt")        ?: 0L
    )
}
