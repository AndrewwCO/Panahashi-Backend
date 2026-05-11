package com.panahashi.services

import com.panahashi.models.CreateProductRequest
import com.panahashi.models.Product
import com.panahashi.services.Collections.PRODUCTS

object ProductService {

    suspend fun getAllProducts(): List<Product> {
        return FirestoreService.getCollection(PRODUCTS).map { doc ->
            doc.toProduct()
        }
    }

    suspend fun getAvailableProducts(): List<Product> {
        return FirestoreService.queryCollection(PRODUCTS, "available", true)
            .map { it.toProduct() }
    }

    suspend fun getProductById(id: String): Product {
        val doc = FirestoreService.getDocument(PRODUCTS, id)
            ?: throw NoSuchElementException("Producto con id=$id no encontrado")
        if (!doc.exists()) throw NoSuchElementException("Producto con id=$id no encontrado")
        return doc.toProduct()
    }

    suspend fun createProduct(request: CreateProductRequest): Product {
        val data = mapOf(
            "name" to request.name,
            "price" to request.price,
            "emoji" to request.emoji,
            "status" to request.status,
            "stock" to request.stock,
            "category" to request.category,
            "description" to request.description,
            "imageUrl" to request.imageUrl,
            "available" to request.available
        )
        val id = FirestoreService.createDocumentAutoId(PRODUCTS, data)
        return getProductById(id)
    }

    suspend fun updateStock(productId: String, newStock: Int): Product {
        if (newStock < 0) throw IllegalArgumentException("El stock no puede ser negativo")
        FirestoreService.updateDocument(
            PRODUCTS, productId,
            mapOf("stock" to newStock, "available" to (newStock > 0))
        )
        return getProductById(productId)
    }

    suspend fun updateProduct(productId: String, fields: Map<String, Any>): Product {
        FirestoreService.updateDocument(PRODUCTS, productId, fields)
        return getProductById(productId)
    }

    suspend fun deleteProduct(productId: String) {
        if (!FirestoreService.exists(PRODUCTS, productId))
            throw NoSuchElementException("Producto con id=$productId no encontrado")
        FirestoreService.deleteDocument(PRODUCTS, productId)
    }

    // ─── Seed productos iniciales ────────────────────────────
    suspend fun seedInitialProducts() {
        val existing = FirestoreService.getCollection(PRODUCTS)
        if (existing.isNotEmpty()) return

        val initialProducts = listOf(
            CreateProductRequest("Sourdough Loaf", 4.50, "🍞", "Ready now", 8, "bread"),
            CreateProductRequest("Baguette", 2.80, "🥖", "Ready now", 3, "bread"),
            CreateProductRequest("Croissant", 2.20, "🥐", "Ready 10:30", 0, "pastry"),
            CreateProductRequest("Multigrain Bread", 5.00, "🍞", "Ready 11:00", 12, "bread"),
        )
        initialProducts.forEach { createProduct(it) }
    }

    // ─── Helpers ─────────────────────────────────────────────
    private fun com.google.cloud.firestore.DocumentSnapshot.toProduct() = Product(
        id = id,
        name = getString("name") ?: "",
        price = getDouble("price") ?: 0.0,
        emoji = getString("emoji") ?: "",
        status = getString("status") ?: "Ready now",
        stock = getLong("stock")?.toInt() ?: 0,
        category = getString("category") ?: "bread",
        description = getString("description") ?: "",
        imageUrl = getString("imageUrl") ?: "",
        available = getBoolean("available") ?: true
    )
}
