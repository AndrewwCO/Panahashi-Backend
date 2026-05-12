package com.panahashi.services

import com.panahashi.models.LoyaltyCard
import com.panahashi.services.Collections.LOYALTY

object LoyaltyService {

    private const val STAMPS_FOR_REWARD = 9   // Cada 9 compras, 1 gratis

    suspend fun getCard(userId: String, bakeryId: String): LoyaltyCard {
        val docId = cardId(userId, bakeryId)
        val doc = FirestoreService.getDocument(LOYALTY, docId)
        if (doc != null && doc.exists()) return doc.toCard()

        // Crear card vacía si no existe
        val card = LoyaltyCard(
            id              = docId,
            userId          = userId,
            bakeryId        = bakeryId,
            stamps          = 0,
            stampsForReward = STAMPS_FOR_REWARD,
            totalRewardsEarned  = 0,
            freeItemsAvailable  = 0,
            updatedAt       = System.currentTimeMillis()
        )
        FirestoreService.createDocument(LOYALTY, docId, cardToMap(card))
        return card
    }

    /** Llamado automáticamente cuando una orden se completa. Suma 1 sello. */
    suspend fun addStamp(userId: String, bakeryId: String): LoyaltyCard {
        val card = getCard(userId, bakeryId)
        val newStamps = card.stamps + 1
        val newRewards = newStamps / STAMPS_FOR_REWARD
        val totalEarned = card.totalRewardsEarned + newRewards
        val freeItems   = card.freeItemsAvailable + newRewards
        val remainingStamps = newStamps % STAMPS_FOR_REWARD

        val updates = mapOf(
            "stamps"               to remainingStamps,
            "totalRewardsEarned"   to totalEarned,
            "freeItemsAvailable"   to freeItems,
            "updatedAt"            to System.currentTimeMillis()
        )
        FirestoreService.updateDocument(LOYALTY, cardId(userId, bakeryId), updates)
        return getCard(userId, bakeryId)
    }

    /** El baker usa este endpoint al entregar el producto gratis (escaneo QR de recompensa). */
    suspend fun redeemReward(userId: String, bakeryId: String): LoyaltyCard {
        val card = getCard(userId, bakeryId)
        if (card.freeItemsAvailable <= 0)
            throw IllegalArgumentException("No tienes recompensas disponibles en esta panadería")

        val updates = mapOf(
            "freeItemsAvailable" to (card.freeItemsAvailable - 1),
            "updatedAt"          to System.currentTimeMillis()
        )
        FirestoreService.updateDocument(LOYALTY, cardId(userId, bakeryId), updates)
        return getCard(userId, bakeryId)
    }

    /** Obtiene todas las tarjetas de un usuario (para mostrar en su perfil). */
    suspend fun getAllCardsForUser(userId: String): List<LoyaltyCard> {
        return FirestoreService.queryCollection(LOYALTY, "userId", userId)
            .map { it.toCard() }
    }

    // ─── Helpers ─────────────────────────────────────────────

    private fun cardId(userId: String, bakeryId: String) = "${userId}_${bakeryId}"

    private fun com.google.cloud.firestore.DocumentSnapshot.toCard() = LoyaltyCard(
        id                 = id,
        userId             = getString("userId")             ?: "",
        bakeryId           = getString("bakeryId")           ?: "",
        stamps             = getLong("stamps")?.toInt()      ?: 0,
        stampsForReward    = getLong("stampsForReward")?.toInt() ?: STAMPS_FOR_REWARD,
        totalRewardsEarned = getLong("totalRewardsEarned")?.toInt() ?: 0,
        freeItemsAvailable = getLong("freeItemsAvailable")?.toInt() ?: 0,
        updatedAt          = getLong("updatedAt")            ?: 0L
    )

    private fun cardToMap(card: LoyaltyCard): Map<String, Any> = mapOf(
        "userId"             to card.userId,
        "bakeryId"           to card.bakeryId,
        "stamps"             to card.stamps,
        "stampsForReward"    to card.stampsForReward,
        "totalRewardsEarned" to card.totalRewardsEarned,
        "freeItemsAvailable" to card.freeItemsAvailable,
        "updatedAt"          to card.updatedAt
    )
}
