package com.panahashi.services

import com.panahashi.models.*
import com.panahashi.models.OrderStatus
import com.panahashi.services.Collections.ORDERS
import java.util.UUID

object OrderService {

    suspend fun createOrder(userId: String, userName: String, request: CreateOrderRequest): Order {
        if (request.items.isEmpty()) throw IllegalArgumentException("La orden no puede estar vacía")

        val bakery = BakeryService.getBakeryById(request.bakeryId)
        if (!bakery.isOpen) throw IllegalArgumentException("${bakery.name} está cerrada en este momento")

        if (!BakeryService.isPickupTimeValid(bakery, request.pickupTime)) {
            throw IllegalArgumentException(
                "La hora de recojo \${request.pickupTime} está fuera del horario de \${bakery.name} " +
                        "(\${bakery.openTime}–\${bakery.closeTime})"
            )
        }

        var total = 0.0
        val validatedItems = request.items.map { item ->
            val product = ProductService.getProductById(item.productId)
            if (product.bakeryId != request.bakeryId)
                throw IllegalArgumentException("El producto \${product.name} no pertenece a \${bakery.name}")
            if (product.stock < item.qty)
                throw IllegalArgumentException("Stock insuficiente para \${product.name}. Disponible: \${product.stock}")
            total += product.price * item.qty
            item.copy(name = product.name, price = product.price, emoji = product.emoji)
        }

        // Calcular descuento de promoción si aplica
        val discountAmount = PromotionService.calculateDiscount(request.promotionId, validatedItems)
        val finalTotal     = (total - discountAmount).coerceAtLeast(0.0)

        val qrCode = UUID.randomUUID().toString().uppercase()

        val orderData = mapOf(
            "userId"         to userId,
            "userName"       to userName,
            "bakeryId"       to request.bakeryId,
            "bakeryName"     to bakery.name,
            "items"          to validatedItems.map { cartItemToMap(it) },
            "total"          to finalTotal,
            "status"         to OrderStatus.PENDING.name,
            "createdAt"      to System.currentTimeMillis(),
            "pickupTime"     to request.pickupTime,
            "scheduledFor"   to (request.scheduledFor ?: 0L),
            "qrCode"         to qrCode,
            // NUEVO
            "notes"          to request.notes,
            "discountAmount" to discountAmount,
            "promotionId"    to request.promotionId,
            "paymentStatus"  to PaymentStatus.PENDING.name,
            "paymentMethod"  to request.paymentMethod.name,
            "estimatedReadyAt" to 0L
        )

        val orderId = FirestoreService.createDocumentAutoId(ORDERS, orderData)

        // Descontar stock
        validatedItems.forEach { item ->
            val product = ProductService.getProductById(item.productId)
            ProductService.updateStock(item.productId, product.stock - item.qty)
        }

        // Limpiar el carrito del usuario
        CartService.clearCart(userId)

        val order = getOrderById(orderId)

        // Notificar al baker solo si CASH_ON_PICKUP (ya está "pagado")
        if (request.paymentMethod == PaymentMethod.CASH_ON_PICKUP) {
            NotificationService.notifyNewOrder(order)
        }

        return order
    }

    suspend fun getOrderById(orderId: String): Order {
        val doc = FirestoreService.getDocument(ORDERS, orderId)
            ?: throw NoSuchElementException("Orden $orderId no encontrada")
        if (!doc.exists()) throw NoSuchElementException("Orden $orderId no encontrada")
        return doc.toOrder()
    }

    suspend fun getOrdersByUser(userId: String, page: Int = 1, pageSize: Int = 20): List<Order> {
        val all = FirestoreService.queryCollection(ORDERS, "userId", userId)
            .map { it.toOrder() }
            .sortedByDescending { it.createdAt }
        return paginate(all, page, pageSize)
    }

    suspend fun getOrdersByBakery(bakeryId: String, page: Int = 1, pageSize: Int = 20): List<Order> {
        val all = FirestoreService.queryCollection(ORDERS, "bakeryId", bakeryId)
            .map { it.toOrder() }
            .sortedByDescending { it.createdAt }
        return paginate(all, page, pageSize)
    }

    suspend fun getPendingOrdersByBakery(bakeryId: String): List<Order> {
        return FirestoreService.queryCollectionMultiple(
            ORDERS,
            mapOf("bakeryId" to bakeryId, "status" to OrderStatus.PENDING.name)
        ).map { it.toOrder() }.sortedBy { it.createdAt }
    }

    // NUEVO: órdenes activas del baker (PENDING + CONFIRMED + BAKING)
    suspend fun getActiveOrdersByBakery(bakeryId: String): List<Order> {
        val activeStatuses = setOf(
            OrderStatus.PENDING.name,
            OrderStatus.CONFIRMED.name,
            OrderStatus.BAKING.name
        )
        return FirestoreService.queryCollection(ORDERS, "bakeryId", bakeryId)
            .map { it.toOrder() }
            .filter { it.status.name in activeStatuses }
            .sortedBy { it.createdAt }
    }

    suspend fun getAllOrders(page: Int = 1, pageSize: Int = 50): List<Order> {
        val all = FirestoreService.getCollection(ORDERS)
            .map { it.toOrder() }
            .sortedByDescending { it.createdAt }
        return paginate(all, page, pageSize)
    }

    suspend fun updateOrderStatus(
        orderId: String,
        newStatus: OrderStatus,
        requestingBakeryId: String? = null
    ): Order {
        if (!FirestoreService.exists(ORDERS, orderId))
            throw NoSuchElementException("Orden $orderId no encontrada")

        val order = getOrderById(orderId)

        if (requestingBakeryId != null && order.bakeryId != requestingBakeryId)
            throw IllegalArgumentException("Esta orden no pertenece a tu panadería")

        // Reponer stock si se cancela
        if (newStatus == OrderStatus.CANCELLED && order.status != OrderStatus.CANCELLED) {
            order.items.forEach { item ->
                val product = ProductService.getProductById(item.productId)
                ProductService.updateStock(item.productId, product.stock + item.qty)
            }
        }

        FirestoreService.updateDocument(ORDERS, orderId, mapOf("status" to newStatus.name))
        val updated = getOrderById(orderId)

        NotificationService.notifyOrderStatusChange(updated, newStatus)

        // Al completar: sumar sello de fidelización
        if (newStatus == OrderStatus.COMPLETED) {
            runCatching { LoyaltyService.addStamp(updated.userId, updated.bakeryId) }
        }

        return updated
    }

    suspend fun setEstimatedReady(orderId: String, estimatedReadyAt: Long, bakeryId: String): Order {
        val order = getOrderById(orderId)
        if (order.bakeryId != bakeryId)
            throw IllegalArgumentException("Esta orden no pertenece a tu panadería")
        FirestoreService.updateDocument(ORDERS, orderId, mapOf("estimatedReadyAt" to estimatedReadyAt))
        return getOrderById(orderId)
    }

    suspend fun verifyQr(qrCode: String, bakeryId: String): Order {
        val orders = FirestoreService.queryCollectionMultiple(
            ORDERS,
            mapOf("qrCode" to qrCode, "bakeryId" to bakeryId)
        )
        val order = orders.firstOrNull()?.toOrder()
            ?: throw NoSuchElementException("QR inválido o no pertenece a esta panadería")

        if (order.status != OrderStatus.READY)
            throw IllegalArgumentException("La orden no está lista. Estado actual: ${order.status}")

        return updateOrderStatus(order.id, OrderStatus.COMPLETED, bakeryId)
    }

    // ─── Helpers ─────────────────────────────────────────────

    private fun <T> paginate(list: List<T>, page: Int, pageSize: Int): List<T> {
        val from = ((page - 1) * pageSize).coerceIn(0, list.size)
        val to   = (from + pageSize).coerceAtMost(list.size)
        return list.subList(from, to)
    }

    @Suppress("UNCHECKED_CAST")
    private fun com.google.cloud.firestore.DocumentSnapshot.toOrder(): Order {
        val rawItems = get("items") as? List<Map<String, Any>> ?: emptyList()
        // FIX: CartItem ya no tiene pickupTime — el campo se ignora si existe en datos viejos
        val items = rawItems.map { map ->
            CartItem(
                productId = map["productId"] as? String ?: "",
                name      = map["name"]      as? String ?: "",
                price     = (map["price"] as? Number)?.toDouble() ?: 0.0,
                emoji     = map["emoji"]     as? String ?: "",
                qty       = (map["qty"] as? Number)?.toInt() ?: 1
            )
        }
        val scheduledForRaw   = getLong("scheduledFor")   ?: 0L
        val estimatedReadyRaw = getLong("estimatedReadyAt") ?: 0L
        return Order(
            id               = id,
            userId           = getString("userId")       ?: "",
            userName         = getString("userName")     ?: "",
            bakeryId         = getString("bakeryId")     ?: "",
            bakeryName       = getString("bakeryName")   ?: "",
            items            = items,
            total            = getDouble("total")        ?: 0.0,
            status           = OrderStatus.valueOf(getString("status") ?: "PENDING"),
            createdAt        = getLong("createdAt")      ?: 0L,
            pickupTime       = getString("pickupTime")   ?: "",
            scheduledFor     = if (scheduledForRaw > 0L) scheduledForRaw else null,
            qrCode           = getString("qrCode")       ?: "",
            notes            = getString("notes")        ?: "",
            estimatedReadyAt = if (estimatedReadyRaw > 0L) estimatedReadyRaw else null,
            discountAmount   = getDouble("discountAmount") ?: 0.0,
            promotionId      = getString("promotionId")  ?: "",
            paymentStatus    = runCatching { PaymentStatus.valueOf(getString("paymentStatus") ?: "PENDING") }
                .getOrDefault(PaymentStatus.PENDING),
            paymentMethod    = runCatching { PaymentMethod.valueOf(getString("paymentMethod") ?: "CASH_ON_PICKUP") }
                .getOrDefault(PaymentMethod.CASH_ON_PICKUP)
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
