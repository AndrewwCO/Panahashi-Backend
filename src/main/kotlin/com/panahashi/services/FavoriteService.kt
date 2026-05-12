package com.panahashi.services

import com.panahashi.models.Bakery
import com.panahashi.models.Favorite
import com.panahashi.services.Collections.FAVORITES

object FavoriteService {

    suspend fun getFavorites(userId: String): List<Bakery> {
        val favDocs = FirestoreService.queryCollection(FAVORITES, "userId", userId)
        return favDocs.mapNotNull { doc ->
            val bakeryId = doc.getString("bakeryId") ?: return@mapNotNull null
            runCatching { BakeryService.getBakeryById(bakeryId) }.getOrNull()
        }
    }

    suspend fun isFavorite(userId: String, bakeryId: String): Boolean {
        val results = FirestoreService.queryCollectionMultiple(
            FAVORITES, mapOf("userId" to userId, "bakeryId" to bakeryId)
        )
        return results.isNotEmpty()
    }

    // Agrega o elimina el favorito (toggle). Retorna true si quedó como favorito.
    suspend fun toggleFavorite(userId: String, bakeryId: String): Boolean {
        // Verificar que la panadería existe
        BakeryService.getBakeryById(bakeryId)

        val existing = FirestoreService.queryCollectionMultiple(
            FAVORITES, mapOf("userId" to userId, "bakeryId" to bakeryId)
        )

        return if (existing.isNotEmpty()) {
            // Ya era favorita → eliminar
            FirestoreService.deleteDocument(FAVORITES, existing.first().id)
            false
        } else {
            // No era favorita → agregar
            val data = mapOf(
                "userId"    to userId,
                "bakeryId"  to bakeryId,
                "createdAt" to System.currentTimeMillis()
            )
            FirestoreService.createDocumentAutoId(FAVORITES, data)
            true
        }
    }

    suspend fun removeFavorite(userId: String, bakeryId: String) {
        val existing = FirestoreService.queryCollectionMultiple(
            FAVORITES, mapOf("userId" to userId, "bakeryId" to bakeryId)
        )
        existing.forEach { FirestoreService.deleteDocument(FAVORITES, it.id) }
    }
}
