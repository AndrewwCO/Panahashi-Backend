package com.panahashi.services

import com.google.cloud.firestore.DocumentSnapshot
import com.google.firebase.cloud.FirestoreClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extensiones de utilidad para Firestore usando corrutinas de Kotlin.
 * Todas las operaciones de Firestore son bloqueantes por diseño del SDK de Admin,
 * por eso se ejecutan en Dispatchers.IO.
 */
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

// Colecciones de Firestore
object Collections {
    const val PRODUCTS = "products"
    const val ORDERS = "orders"
    const val USERS = "users"
}
