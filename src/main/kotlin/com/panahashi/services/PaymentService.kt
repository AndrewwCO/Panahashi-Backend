package com.panahashi.services

import com.panahashi.models.*
import com.panahashi.services.Collections.ORDERS
import com.panahashi.services.Collections.PAYMENTS

/**
 * Servicio de pagos SIMULADO.
 * En producción, este servicio se conectaría a Stripe / MercadoPago.
 * Por ahora, simula aprobación/rechazo con una lógica simple.
 */
object PaymentService {

    suspend fun createPayment(userId: String, request: CreatePaymentRequest): Payment {
        // Verificar que la orden existe y pertenece al usuario
        val order = OrderService.getOrderById(request.orderId)
        if (order.userId != userId)
            throw IllegalArgumentException("Esta orden no te pertenece")
        if (order.paymentStatus == PaymentStatus.APPROVED)
            throw IllegalArgumentException("Esta orden ya fue pagada")

        // Simular procesamiento de pago
        val paymentStatus = simulatePayment(request)

        val data = mapOf(
            "orderId"             to request.orderId,
            "userId"              to userId,
            "amount"              to order.total - order.discountAmount,
            "method"              to request.method.name,
            "status"              to paymentStatus.name,
            "simulatedCardLast4"  to request.simulatedCardLast4,
            "createdAt"           to System.currentTimeMillis(),
            "updatedAt"           to System.currentTimeMillis()
        )
        val paymentId = FirestoreService.createDocumentAutoId(PAYMENTS, data)

        // Actualizar estado de pago en la orden
        FirestoreService.updateDocument(ORDERS, request.orderId, mapOf(
            "paymentStatus" to paymentStatus.name,
            "paymentMethod" to request.method.name
        ))

        // Si el pago fue aprobado, notificar al baker
        if (paymentStatus == PaymentStatus.APPROVED) {
            val updatedOrder = OrderService.getOrderById(request.orderId)
            NotificationService.notifyNewOrder(updatedOrder)
        }

        val payDoc = FirestoreService.getDocument(PAYMENTS, paymentId)!!
        return payDoc.toPayment(paymentId)
    }

    suspend fun getPaymentByOrder(orderId: String): Payment? {
        val docs = FirestoreService.queryCollection(PAYMENTS, "orderId", orderId)
        val doc = docs.firstOrNull() ?: return null
        return doc.toPayment(doc.id)
    }

    suspend fun refundPayment(orderId: String): Payment {
        val payment = getPaymentByOrder(orderId)
            ?: throw NoSuchElementException("No se encontró el pago para la orden $orderId")
        if (payment.status != PaymentStatus.APPROVED)
            throw IllegalArgumentException("Solo se pueden reembolsar pagos aprobados")

        FirestoreService.updateDocument(PAYMENTS, payment.id, mapOf(
            "status"    to PaymentStatus.REFUNDED.name,
            "updatedAt" to System.currentTimeMillis()
        ))
        FirestoreService.updateDocument(ORDERS, orderId, mapOf(
            "paymentStatus" to PaymentStatus.REFUNDED.name
        ))
        return getPaymentByOrder(orderId)!!
    }

    // ─── Simulación de pago ───────────────────────────────────
    /**
     * Simula el resultado del pago:
     * - CASH_ON_PICKUP → siempre APPROVED (paga en tienda)
     * - Tarjeta que termina en 0000 → REJECTED (simula fallo)
     * - Cualquier otra → APPROVED
     */
    private fun simulatePayment(request: CreatePaymentRequest): PaymentStatus {
        if (request.method == PaymentMethod.CASH_ON_PICKUP) return PaymentStatus.APPROVED
        if (request.simulatedCardLast4 == "0000") return PaymentStatus.REJECTED
        return PaymentStatus.APPROVED
    }

    // ─── Helpers ─────────────────────────────────────────────

    private fun com.google.cloud.firestore.DocumentSnapshot.toPayment(docId: String) = Payment(
        id                  = docId,
        orderId             = getString("orderId")            ?: "",
        userId              = getString("userId")             ?: "",
        amount              = getDouble("amount")             ?: 0.0,
        method              = PaymentMethod.valueOf(getString("method") ?: "CASH_ON_PICKUP"),
        status              = PaymentStatus.valueOf(getString("status") ?: "PENDING"),
        simulatedCardLast4  = getString("simulatedCardLast4") ?: "",
        createdAt           = getLong("createdAt")            ?: 0L,
        updatedAt           = getLong("updatedAt")            ?: 0L
    )
}
