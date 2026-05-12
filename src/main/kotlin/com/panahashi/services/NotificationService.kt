package com.panahashi.services

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.panahashi.models.Order
import com.panahashi.models.OrderStatus
import com.panahashi.services.Collections.USERS
import org.slf4j.LoggerFactory

object NotificationService {

    private val logger = LoggerFactory.getLogger("NotificationService")
    private val fcm get() = FirebaseMessaging.getInstance()

    // ─── Notificaciones de órdenes ───────────────────────────

    /** Notifica al BAKER que llegó una nueva orden. */
    suspend fun notifyNewOrder(order: Order) {
        val bakery = runCatching { BakeryService.getBakeryById(order.bakeryId) }.getOrNull() ?: return
        val bakerToken = getFcmToken(bakery.ownerId) ?: return

        sendToToken(
            token = bakerToken,
            title = "🍞 Nueva orden recibida",
            body  = "${order.userName} ordenó ${order.items.size} producto(s) — pickup a las ${order.pickupTime}",
            data  = mapOf(
                "type"     to "NEW_ORDER",
                "orderId"  to order.id,
                "bakeryId" to order.bakeryId
            )
        )
    }

    /** Notifica al CLIENTE según el nuevo estado de su orden. */
    suspend fun notifyOrderStatusChange(order: Order, newStatus: OrderStatus) {
        val clientToken = getFcmToken(order.userId) ?: return

        val (title, body) = when (newStatus) {
            OrderStatus.CONFIRMED -> Pair(
                "✅ Orden confirmada",
                "${order.bakeryName} confirmó tu orden. Pickup a las ${order.pickupTime}"
            )
            // NUEVO: estado BAKING
            OrderStatus.BAKING -> Pair(
                "👨‍🍳 ¡Están horneando tu pan!",
                "${order.bakeryName} ya está preparando tu pedido."
            )
            OrderStatus.READY -> Pair(
                "🥖 ¡Tu pan está listo!",
                "Tu orden en ${order.bakeryName} está lista para recoger. Muestra tu QR."
            )
            OrderStatus.COMPLETED -> Pair(
                "🎉 Orden completada",
                "¡Gracias por tu compra en ${order.bakeryName}! Buen provecho."
            )
            OrderStatus.CANCELLED -> Pair(
                "❌ Orden cancelada",
                "Tu orden en ${order.bakeryName} fue cancelada."
            )
            else -> return // PENDING no genera notificación al cliente
        }

        sendToToken(
            token = clientToken,
            title = title,
            body  = body,
            data  = mapOf(
                "type"     to "ORDER_STATUS_CHANGE",
                "orderId"  to order.id,
                "status"   to newStatus.name
            )
        )
    }

    /** Notifica al BAKER que su panadería fue activada por el admin. */
    suspend fun notifyBakeryActivated(ownerId: String, bakeryName: String) {
        val token = getFcmToken(ownerId) ?: return
        sendToToken(
            token = token,
            title = "🎊 ¡Tu panadería está activa!",
            body  = "$bakeryName ya aparece en el mapa para los clientes.",
            data  = mapOf("type" to "BAKERY_ACTIVATED")
        )
    }

    /** Notifica al CLIENTE que su orden tiene un tiempo estimado actualizado. */
    suspend fun notifyEstimatedReady(order: com.panahashi.models.Order, estimatedReadyAt: Long) {
        val clientToken = getFcmToken(order.userId) ?: return
        val time = java.time.Instant.ofEpochMilli(estimatedReadyAt)
            .atZone(java.time.ZoneId.of("America/Bogota"))
            .let { String.format("%02d:%02d", it.hour, it.minute) }
        sendToToken(
            token = clientToken,
            title = "⏱ Tiempo estimado actualizado",
            body  = "${order.bakeryName} estima que tu pedido estará listo a las $time",
            data  = mapOf(
                "type"             to "ESTIMATED_READY",
                "orderId"          to order.id,
                "estimatedReadyAt" to estimatedReadyAt.toString()
            )
        )
    }

    /** Notifica al CLIENTE que ganó un sello de fidelización. */
    suspend fun notifyLoyaltyStamp(userId: String, bakeryName: String, stamps: Int, stampsForReward: Int) {
        val clientToken = getFcmToken(userId) ?: return
        val message = if (stamps == 0) {
            "🎉 ¡Ganaste una recompensa en $bakeryName! Canjeala en tu próxima visita."
        } else {
            "🥖 Llevas $stamps/$stampsForReward sellos en $bakeryName. ¡Sigue así!"
        }
        sendToToken(
            token = clientToken,
            title = "Tarjeta de fidelización",
            body  = message,
            data  = mapOf("type" to "LOYALTY_STAMP", "bakeryName" to bakeryName)
        )
    }

    /** Notifica al CLIENTE que su pago fue rechazado (simulado). */
    suspend fun notifyPaymentRejected(userId: String, orderId: String) {
        val clientToken = getFcmToken(userId) ?: return
        sendToToken(
            token = clientToken,
            title = "❌ Pago rechazado",
            body  = "No pudimos procesar tu pago. Intenta con otro método.",
            data  = mapOf("type" to "PAYMENT_REJECTED", "orderId" to orderId)
        )
    }

    // ─── Helpers internos ────────────────────────────────────

    private suspend fun getFcmToken(uid: String): String? {
        val doc = FirestoreService.getDocument(USERS, uid) ?: return null
        return doc.getString("fcmToken")?.takeIf { it.isNotEmpty() }
    }

    private fun sendToToken(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        runCatching {
            val message = Message.builder()
                .setToken(token)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build()
                )
                .putAllData(data)
                .build()

            val response = fcm.send(message)
            logger.info("Notificación enviada: $response → $title")
        }.onFailure { e ->
            logger.warn("No se pudo enviar notificación a $token: ${e.message}")
        }
    }
}
