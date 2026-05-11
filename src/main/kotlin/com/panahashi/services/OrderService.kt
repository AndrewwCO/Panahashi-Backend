package com.panahashi.services

import com.panahashi.models.*
import com.panahashi.services.Collections.ORDERS
import com.panahashi.services.Collections.PRODUCTS
import java.util.UUID

object OrderService {

    suspend fun createOrder(userId: String, userName: String, request: CreateOrderRequest): Order {
        if (request.items.isEmpty()) throw IllegalArgumentException("La orden no puede estar vacía")

        // Validar stock disponible y calcular total
        var total = 0.0
        val validatedItems = request.items.map { item ->
            val product = ProductService.getProductById(item.productId)
            if (product.stock < item.qty)
                throw IllegalArgumentException("Stock insuficiente para ${product.name}. Disponible: ${product.stock}")
            total += product.price * item.qty
            item.copy(name = product.name, price = product.price, emoji = product.emoji)
        }

        // Generar QR code (UUID como identificador único)
        val qrCode = UUID.randomUUID().toString().uppercase()

        val orderData = mapOf(
            "userId" to userId,
            "userName" to userName,
            "items" to validatedItems.map { cartItemToMap(it) },
            "total" to total,
            "status" to OrderStatus.PENDING.name,
            "createdAt" to System.currentTimeMillis(),
            "pickupTime" to request.pickupTime,
            "qrCode" to qrCode
        )

        val orderId = FirestoreService.createDocumentAutoId(ORDERS, orderData)

        // Descontar stock
        validatedItems.forEach { item ->
            val product = ProductService.getProductById(item.productId)
            ProductService.updateStock(item.productId, product.stock - item.qty)
        }

        return getOrderById(orderId)
    }

    suspend fun getOrderById(orderId: String): Order {
        val doc = FirestoreService.getDocument(ORDERS, orderId)
            ?: throw NoSuchElementException("Orden $orderId no encontrada")
        if (!doc.exists()) throw NoSuchElementException("Orden $orderId no encontrada")
        return doc.toOrder()
    }

    suspend fun getOrdersByUser(userId: String): List<Order> {
        return FirestoreService.queryCollection(ORDERS, "userId", userId)
            .map { it.toOrder() }
            .sortedByDescending { it.createdAt }
    }

    suspend fun getAllOrders(): List<Order> {
        return FirestoreService.getCollection(ORDERS)
            .map { it.toOrder() }
            .sortedByDescending { it.createdAt }
    }

    suspend fun updateOrderStatus(orderId: String, newStatus: OrderStatus): Order {
        if (!FirestoreService.exists(ORDERS, orderId))
            throw NoSuchElementException("Orden $orderId no encontrada")

        // Si se cancela, reponer stock
        if (newStatus == OrderStatus.CANCELLED) {
            val order = getOrderById(orderId)
            if (order.status != OrderStatus.CANCELLED) {
                order.items.forEach { item ->
                    val product = ProductService.getProductById(item.productId)
                    ProductService.updateStock(item.productId, product.stock + item.qty)
                }
            }
        }

        FirestoreService.updateDocument(ORDERS, orderId, mapOf("status" to newStatus.name))
        return getOrderById(orderId)
    }

    // ─── Helpers ─────────────────────────────────────────────
    @Suppress("UNCHECKED_CAST")
    private fun com.google.cloud.firestore.DocumentSnapshot.toOrder(): Order {
        val rawItems = get("items") as? List<Map<String, Any>> ?: emptyList()
        val items = rawItems.map { map ->
            CartItem(
                productId = map["productId"] as? String ?: "",
                name = map["name"] as? String ?: "",
                price = (map["price"] as? Number)?.toDouble() ?: 0.0,
                emoji = map["emoji"] as? String ?: "",
                qty = (map["qty"] as? Number)?.toInt() ?: 1,
                pickupTime = map["pickupTime"] as? String ?: ""
            )
        }
        return Order(
            id = id,
            userId = getString("userId") ?: "",
            userName = getString("userName") ?: "",
            items = items,
            total = getDouble("total") ?: 0.0,
            status = OrderStatus.valueOf(getString("status") ?: "PENDING"),
            createdAt = getLong("createdAt") ?: 0L,
            pickupTime = getString("pickupTime") ?: "",
            qrCode = getString("qrCode") ?: ""
        )
    }

    private fun cartItemToMap(item: CartItem): Map<String, Any> = mapOf(
        "productId" to item.productId,
        "name" to item.name,
        "price" to item.price,
        "emoji" to item.emoji,
        "qty" to item.qty,
        "pickupTime" to item.pickupTime
    )
}
