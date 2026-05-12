package com.panahashi.services

import com.panahashi.models.*
import com.panahashi.services.Collections.CARTS

object CartService {

    suspend fun getCart(userId: String): Cart? {
        val doc = FirestoreService.getDocument(CARTS, userId) ?: return null
        if (!doc.exists()) return null
        return doc.toCart()
    }

    suspend fun addToCart(userId: String, request: AddToCartRequest): Cart {
        val product = ProductService.getProductById(request.productId)
        val bakery  = BakeryService.getBakeryById(request.bakeryId)

        if (product.bakeryId != request.bakeryId)
            throw IllegalArgumentException("El producto no pertenece a esa panadería")
        if (!product.available)
            throw IllegalArgumentException("${product.name} no está disponible en este momento")
        if (request.qty <= 0)
            throw IllegalArgumentException("La cantidad debe ser mayor a 0")

        val existingCart = getCart(userId)

        // Si el carrito tiene productos de otra panadería, limpiar primero
        val currentItems: MutableList<CartItem> = if (existingCart != null && existingCart.bakeryId != request.bakeryId) {
            mutableListOf()
        } else {
            existingCart?.items?.toMutableList() ?: mutableListOf()
        }

        val idx = currentItems.indexOfFirst { it.productId == request.productId }
        if (idx >= 0) {
            val updated = currentItems[idx].copy(qty = currentItems[idx].qty + request.qty)
            currentItems[idx] = updated
        } else {
            currentItems.add(CartItem(
                productId = product.id,
                name      = product.name,
                price     = product.price,
                emoji     = product.emoji,
                qty       = request.qty
            ))
        }

        val cartData = mapOf(
            "userId"      to userId,
            "bakeryId"    to request.bakeryId,
            "bakeryName"  to bakery.name,
            "items"       to currentItems.map { cartItemToMap(it) },
            "updatedAt"   to System.currentTimeMillis()
        )
        FirestoreService.createDocument(CARTS, userId, cartData)

        return getCart(userId)!!
    }

    suspend fun updateCartItem(userId: String, request: UpdateCartItemRequest): Cart {
        val cart = getCart(userId) ?: throw NoSuchElementException("No tienes un carrito activo")
        val items = cart.items.toMutableList()

        if (request.qty == 0) {
            items.removeIf { it.productId == request.productId }
        } else {
            val idx = items.indexOfFirst { it.productId == request.productId }
            if (idx < 0) throw NoSuchElementException("Producto no encontrado en el carrito")
            items[idx] = items[idx].copy(qty = request.qty)
        }

        val cartData = mapOf(
            "items"     to items.map { cartItemToMap(it) },
            "updatedAt" to System.currentTimeMillis()
        )
        FirestoreService.updateDocument(CARTS, userId, cartData)
        return getCart(userId)!!
    }

    suspend fun clearCart(userId: String) {
        val cartData = mapOf(
            "items"     to emptyList<Map<String, Any>>(),
            "bakeryId"  to "",
            "bakeryName" to "",
            "updatedAt" to System.currentTimeMillis()
        )
        if (FirestoreService.exists(CARTS, userId)) {
            FirestoreService.updateDocument(CARTS, userId, cartData)
        }
    }

    // ─── Helpers ─────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun com.google.cloud.firestore.DocumentSnapshot.toCart(): Cart {
        val rawItems = get("items") as? List<Map<String, Any>> ?: emptyList()
        val items = rawItems.map { map ->
            CartItem(
                productId = map["productId"] as? String ?: "",
                name      = map["name"]      as? String ?: "",
                price     = (map["price"] as? Number)?.toDouble() ?: 0.0,
                emoji     = map["emoji"]     as? String ?: "",
                qty       = (map["qty"] as? Number)?.toInt() ?: 1
            )
        }
        return Cart(
            userId      = id,
            bakeryId    = getString("bakeryId")   ?: "",
            bakeryName  = getString("bakeryName") ?: "",
            items       = items,
            updatedAt   = getLong("updatedAt")    ?: 0L
        )
    }

    private fun cartItemToMap(item: CartItem): Map<String, Any> = mapOf(
        "productId" to item.productId,
        "name"      to item.name,
        "price"     to item.price,
        "emoji"     to item.emoji,
        "qty"       to item.qty
    )
}
