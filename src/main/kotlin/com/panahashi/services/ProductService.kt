package com.panahashi.services

import com.panahashi.models.*
import com.panahashi.services.Collections.PRODUCTS

object ProductService {

    suspend fun getAllProducts(): List<Product> {
        return FirestoreService.getCollection(PRODUCTS).map { it.toProduct() }
    }

    // Productos disponibles de UNA panadería (para la app cliente)
    suspend fun getAvailableProductsByBakery(bakeryId: String): List<Product> {
        return FirestoreService.queryCollectionMultiple(
            PRODUCTS,
            mapOf("bakeryId" to bakeryId, "available" to true)
        ).map { it.toProduct() }
    }

    // Todos los productos de UNA panadería (para la app del baker, incluye sin stock)
    suspend fun getAllProductsByBakery(bakeryId: String): List<Product> {
        return FirestoreService.queryCollection(PRODUCTS, "bakeryId", bakeryId)
            .map { it.toProduct() }
            .sortedBy { it.name }
    }

    suspend fun getProductById(id: String): Product {
        val doc = FirestoreService.getDocument(PRODUCTS, id)
            ?: throw NoSuchElementException("Producto con id=$id no encontrado")
        if (!doc.exists()) throw NoSuchElementException("Producto con id=$id no encontrado")
        return doc.toProduct()
    }

    // bakeryId viene del token del baker, no del body del request
    suspend fun createProduct(bakeryId: String, request: CreateProductRequest): Product {
        val data = mapOf(
            "bakeryId"           to bakeryId,
            "name"               to request.name,
            "price"              to request.price,
            "emoji"              to request.emoji,
            "availabilityStatus" to request.availabilityStatus.name,
            "stock"              to request.stock,
            "category"           to request.category,
            "description"        to request.description,
            "imageUrl"           to request.imageUrl,
            "advanceMinutes"     to request.advanceMinutes,
            "available"          to (request.stock > 0 && request.available)

        )
        val id = FirestoreService.createDocumentAutoId(PRODUCTS, data)
        return getProductById(id)
    }

    suspend fun updateProduct(productId: String, request: UpdateProductRequest): Product {
        val updates = mutableMapOf<String, Any>()
        request.name?.let               { updates["name"]               = it }
        request.price?.let              { updates["price"]              = it }
        request.emoji?.let              { updates["emoji"]              = it }
        request.availabilityStatus?.let { updates["availabilityStatus"] = it.name }
        request.category?.let           { updates["category"]           = it }
        request.description?.let        { updates["description"]        = it }
        request.imageUrl?.let           { updates["imageUrl"]           = it }
        request.advanceMinutes?.let     { updates["advanceMinutes"]     = it }
        request.stock?.let     { updates["stock"]     = it }
        request.available?.let { updates["available"] = it }



        if (updates.isNotEmpty()) {
            FirestoreService.updateDocument(PRODUCTS, productId, updates)
        }
        return getProductById(productId)
    }

    suspend fun updateStock(productId: String, newStock: Int): Product {
        if (newStock < 0) throw IllegalArgumentException("El stock no puede ser negativo")
        FirestoreService.updateDocument(
            PRODUCTS, productId,
            mapOf(
                "stock"     to newStock,
                "available" to (newStock > 0)
            )
        )
        return getProductById(productId)
    }

    suspend fun deleteProduct(productId: String) {
        if (!FirestoreService.exists(PRODUCTS, productId))
            throw NoSuchElementException("Producto con id=$productId no encontrado")
        FirestoreService.deleteDocument(PRODUCTS, productId)
    }

    // Solo el baker dueño puede modificar sus productos
    suspend fun assertProductBelongsToBakery(productId: String, bakeryId: String) {
        val product = getProductById(productId)
        if (product.bakeryId != bakeryId) {
            throw IllegalArgumentException("Este producto no pertenece a tu panadería")
        }
    }

    // ─── Seed (ahora usa el nuevo enum) ─────────────────────
    suspend fun seedInitialProducts(bakeryId: String) {
        val existing = FirestoreService.queryCollection(PRODUCTS, "bakeryId", bakeryId)
        if (existing.isNotEmpty()) return

        val initialProducts = listOf(
            CreateProductRequest("Pan de masa madre", 12_000.0, "🍞", ProductAvailabilityStatus.READY_NOW,  8, "pan"),
            CreateProductRequest("Baguette",           8_500.0, "🥖", ProductAvailabilityStatus.READY_NOW,  3, "pan"),
            CreateProductRequest("Croissant",          5_500.0, "🥐", ProductAvailabilityStatus.READY_IN_20, 0, "pastelería"),
            CreateProductRequest("Pan integral",      10_000.0, "🍞", ProductAvailabilityStatus.ADVANCE_ORDER_ONLY, 12, "pan", advanceMinutes = 120),
        )
        initialProducts.forEach { createProduct(bakeryId, it) }
    }

    // ─── Helpers ─────────────────────────────────────────────
    private fun com.google.cloud.firestore.DocumentSnapshot.toProduct(): Product {
        // Retrocompatibilidad: si existe el campo viejo "status" como String libre, mapear al enum
        val rawStatus = getString("availabilityStatus")
            ?: getString("status")  // campo viejo
            ?: ProductAvailabilityStatus.READY_NOW.name
        val availStatus = runCatching {
            ProductAvailabilityStatus.valueOf(rawStatus)
        }.getOrElse {
            // Si el valor viejo era "Ready now", "Ready 10:30", etc., mapear al más cercano
            when {
                rawStatus.contains("now", ignoreCase = true) -> ProductAvailabilityStatus.READY_NOW
                rawStatus.contains("order", ignoreCase = true) -> ProductAvailabilityStatus.ADVANCE_ORDER_ONLY
                else -> ProductAvailabilityStatus.READY_IN_20
            }
        }

        return Product(
            id                 = id,
            bakeryId           = getString("bakeryId")    ?: "",
            name               = getString("name")        ?: "",
            price              = getDouble("price")       ?: 0.0,
            emoji              = getString("emoji")       ?: "",
            availabilityStatus = availStatus,
            stock              = getLong("stock")?.toInt() ?: 0,
            category           = getString("category")    ?: "bread",
            description        = getString("description") ?: "",
            imageUrl           = getString("imageUrl")    ?: "",
            available          = getBoolean("available")  ?: true,
            advanceMinutes     = getLong("advanceMinutes")?.toInt() ?: 0
        )
    }
}
