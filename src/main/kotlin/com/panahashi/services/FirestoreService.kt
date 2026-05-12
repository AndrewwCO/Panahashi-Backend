package com.panahashi.services

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Query
import com.google.firebase.cloud.FirestoreClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FirestoreService {

    val db get() = FirestoreClient.getFirestore()

    // ─── GET ────────────────────────────────────────────────
    suspend fun getDocument(collection: String, id: String): DocumentSnapshot? =
        withContext(Dispatchers.IO) {
            db.collection(collection).document(id).get().get()
        }

    suspend fun getCollection(collection: String): List<DocumentSnapshot> =
        withContext(Dispatchers.IO) {
            db.collection(collection).get().get().documents
        }

    suspend fun queryCollection(
        collection: String,
        field: String,
        value: Any
    ): List<DocumentSnapshot> =
        withContext(Dispatchers.IO) {
            db.collection(collection)
                .whereEqualTo(field, value)
                .get()
                .get()
                .documents
        }

    // Query con múltiples filtros iguales
    suspend fun queryCollectionMultiple(
        collection: String,
        filters: Map<String, Any>
    ): List<DocumentSnapshot> =
        withContext(Dispatchers.IO) {
            var query: Query = db.collection(collection)
            filters.forEach { (field, value) ->
                query = query.whereEqualTo(field, value)
            }
            query.get().get().documents
        }

    // NUEVO: query con un filtro y ordenamiento
    suspend fun queryCollectionOrdered(
        collection: String,
        field: String,
        value: Any,
        orderByField: String,
        descending: Boolean = false
    ): List<DocumentSnapshot> =
        withContext(Dispatchers.IO) {
            val direction = if (descending) Query.Direction.DESCENDING else Query.Direction.ASCENDING
            db.collection(collection)
                .whereEqualTo(field, value)
                .orderBy(orderByField, direction)
                .get()
                .get()
                .documents
        }

    // NUEVO: contar documentos (sin traer el contenido completo)
    suspend fun countCollection(collection: String, field: String, value: Any): Long =
        withContext(Dispatchers.IO) {
            db.collection(collection)
                .whereEqualTo(field, value)
                .get()
                .get()
                .size()
                .toLong()
        }

    // ─── CREATE ─────────────────────────────────────────────
    suspend fun createDocument(collection: String, id: String, data: Map<String, Any>): String =
        withContext(Dispatchers.IO) {
            db.collection(collection).document(id).set(data).get()
            id
        }

    suspend fun createDocumentAutoId(collection: String, data: Map<String, Any>): String =
        withContext(Dispatchers.IO) {
            val ref = db.collection(collection).document()
            ref.set(data + mapOf("id" to ref.id)).get()
            ref.id
        }

    // ─── UPDATE ─────────────────────────────────────────────
    suspend fun updateDocument(collection: String, id: String, data: Map<String, Any>) =
        withContext(Dispatchers.IO) {
            db.collection(collection).document(id).update(data).get()
        }

    // ─── DELETE ─────────────────────────────────────────────
    suspend fun deleteDocument(collection: String, id: String) =
        withContext(Dispatchers.IO) {
            db.collection(collection).document(id).delete().get()
        }

    // ─── EXISTS ─────────────────────────────────────────────
    suspend fun exists(collection: String, id: String): Boolean =
        withContext(Dispatchers.IO) {
            db.collection(collection).document(id).get().get().exists()
        }
}

// ─── Colecciones de Firestore ────────────────────────────────
object Collections {
    const val PRODUCTS   = "products"
    const val ORDERS     = "orders"
    const val USERS      = "users"
    const val BAKERIES   = "bakeries"
    const val REVIEWS    = "reviews"
    const val FAVORITES  = "favorites"    // NUEVO
    const val CARTS      = "carts"        // NUEVO
    const val PROMOTIONS = "promotions"   // NUEVO
    const val LOYALTY    = "loyalty"      // NUEVO
    const val PAYMENTS   = "payments"     // NUEVO
}
