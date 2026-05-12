package com.panahashi.models

import kotlinx.serialization.Serializable

// ─── ROLES ───────────────────────────────────────────────────
enum class UserRole {
    CUSTOMER,   // App de clientes
    BAKER,      // App de panaderías
    ADMIN       // Tú
}

// ─── PANADERÍA ───────────────────────────────────────────────
@Serializable
data class Bakery(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val address: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val phone: String = "",
    val logoUrl: String = "",
    val bannerUrl: String = "",
    val isOpen: Boolean = false,
    val openTime: String = "07:00",
    val closeTime: String = "14:00",
    val rating: Double = 0.0,
    val totalReviews: Int = 0,
    val status: BakeryStatus = BakeryStatus.ACTIVE,
    val ownerId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class BakeryStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}

@Serializable
data class CreateBakeryRequest(
    val name: String,
    val description: String = "",
    val address: String,
    val lat: Double,
    val lng: Double,
    val phone: String = "",
    val openTime: String = "07:00",
    val closeTime: String = "14:00",
    val ownerId: String
)

@Serializable
data class UpdateBakeryRequest(
    val name: String? = null,
    val description: String? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val phone: String? = null,
    val logoUrl: String? = null,
    val bannerUrl: String? = null,
    val isOpen: Boolean? = null,
    val openTime: String? = null,
    val closeTime: String? = null,
    val status: BakeryStatus? = null
)

// ─── PRODUCTO ────────────────────────────────────────────────

// FIX: status del producto ahora es un enum tipado, no un String libre
@Serializable
enum class ProductAvailabilityStatus {
    READY_NOW,           // Listo para llevar
    READY_IN_20,         // Listo en ~20 min
    READY_IN_60,         // Listo en ~1 hora
    ADVANCE_ORDER_ONLY,  // Solo con pedido anticipado
    OUT_OF_STOCK         // Sin stock
}

@Serializable
data class Product(
    val id: String = "",
    val bakeryId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val emoji: String = "",
    // FIX: ahora es el enum, con retrocompatibilidad via String en Firestore
    val availabilityStatus: ProductAvailabilityStatus = ProductAvailabilityStatus.READY_NOW,
    val stock: Int = 0,
    val category: String = "bread",
    val description: String = "",
    val imageUrl: String = "",
    val available: Boolean = true,
    // NUEVO: tiempo mínimo de anticipación en minutos (0 = no requiere anticipación)
    val advanceMinutes: Int = 0
)

@Serializable
data class CreateProductRequest(
    val name: String,
    val price: Double,
    val emoji: String,
    val availabilityStatus: ProductAvailabilityStatus = ProductAvailabilityStatus.READY_NOW,
    val stock: Int = 0,
    val category: String = "bread",
    val description: String = "",
    val imageUrl: String = "",
    val available: Boolean = true,
    val advanceMinutes: Int = 0
)

@Serializable
data class UpdateProductRequest(
    val name: String? = null,
    val price: Double? = null,
    val emoji: String? = null,
    val availabilityStatus: ProductAvailabilityStatus? = null,
    val category: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val advanceMinutes: Int? = null
)

@Serializable
data class UpdateStockRequest(
    val stock: Int
)

// ─── CARRITO ─────────────────────────────────────────────────
// FIX: eliminado pickupTime de CartItem — solo vive en Order
@Serializable
data class CartItem(
    val productId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val emoji: String = "",
    val qty: Int = 1
)

// ─── ORDEN ───────────────────────────────────────────────────
@Serializable
data class Order(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val bakeryId: String = "",
    val bakeryName: String = "",
    val items: List<CartItem> = emptyList(),
    val total: Double = 0.0,
    val status: OrderStatus = OrderStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val pickupTime: String = "",
    val scheduledFor: Long? = null,
    val qrCode: String = "",
    // NUEVO: notas del cliente (alergias, instrucciones)
    val notes: String = "",
    // NUEVO: tiempo estimado que pone el baker al confirmar (epoch millis)
    val estimatedReadyAt: Long? = null,
    // NUEVO: descuento de promoción aplicado
    val discountAmount: Double = 0.0,
    val promotionId: String = "",
    // NUEVO: pago simulado
    val paymentStatus: PaymentStatus = PaymentStatus.PENDING,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH_ON_PICKUP
)

@Serializable
enum class OrderStatus {
    PENDING,
    CONFIRMED,
    BAKING,     // NUEVO: el baker empezó a hornear
    READY,
    COMPLETED,
    CANCELLED
}

@Serializable
data class CreateOrderRequest(
    val bakeryId: String,
    val items: List<CartItem>,
    val pickupTime: String,
    val scheduledFor: Long? = null,
    // NUEVO: notas del cliente (alergias, sin azúcar, etc.)
    val notes: String = "",
    // NUEVO: método de pago
    val paymentMethod: PaymentMethod = PaymentMethod.CASH_ON_PICKUP,
    // NUEVO: id de promoción a aplicar (opcional)
    val promotionId: String = ""
)

@Serializable
data class UpdateOrderStatusRequest(
    val status: OrderStatus
)

// ─── RESEÑAS ─────────────────────────────────────────────────
// NUEVO: sistema de reviews completo
@Serializable
data class Review(
    val id: String = "",
    val bakeryId: String = "",
    val userId: String = "",
    val userName: String = "",
    val orderId: String = "",   // solo se puede reseñar después de una orden completada
    val rating: Int = 5,        // 1–5
    val comment: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class CreateReviewRequest(
    val orderId: String,        // se valida que la orden sea COMPLETED y sea del usuario
    val rating: Int,            // 1–5
    val comment: String = ""
)

// ─── USUARIO ─────────────────────────────────────────────────
@Serializable
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = UserRole.CUSTOMER.name,
    val bakeryId: String = "",
    val fcmToken: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val phone: String? = null,
    val fcmToken: String? = null
)

@Serializable
data class RegisterRequest(
    val displayName: String,
    val phone: String = "",
    val role: String = UserRole.CUSTOMER.name
)

// ─── GEO ─────────────────────────────────────────────────────
@Serializable
data class NearbyRequest(
    val lat: Double,
    val lng: Double,
    val radiusKm: Double = 5.0
)

// ─── PAGINACIÓN ──────────────────────────────────────────────
// NUEVO: wrapper de respuesta paginada
@Serializable
data class PagedResponse<T>(
    val data: List<T>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val hasMore: Boolean
)

// ─── RESPUESTAS ──────────────────────────────────────────────
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null
)

// ─── PAGO SIMULADO ───────────────────────────────────────────
@Serializable
enum class PaymentStatus {
    PENDING,    // Esperando confirmación simulada
    APPROVED,   // Pago aprobado
    REJECTED,   // Pago rechazado (simulado)
    REFUNDED    // Reembolso
}

@Serializable
enum class PaymentMethod {
    CREDIT_CARD,
    DEBIT_CARD,
    PSE,
    CASH_ON_PICKUP   // Pago en la tienda al recoger
}

@Serializable
data class Payment(
    val id: String = "",
    val orderId: String = "",
    val userId: String = "",
    val amount: Double = 0.0,
    val method: PaymentMethod = PaymentMethod.CASH_ON_PICKUP,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val simulatedCardLast4: String = "",    // Últimos 4 dígitos (simulado)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class CreatePaymentRequest(
    val orderId: String,
    val method: PaymentMethod,
    val simulatedCardLast4: String = ""     // Solo para tarjeta (simulación)
)

// ─── FAVORITOS ───────────────────────────────────────────────
@Serializable
data class Favorite(
    val id: String = "",
    val userId: String = "",
    val bakeryId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// ─── CARRITO PERSISTENTE ─────────────────────────────────────
@Serializable
data class Cart(
    val userId: String = "",
    val bakeryId: String = "",
    val bakeryName: String = "",
    val items: List<CartItem> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class AddToCartRequest(
    val bakeryId: String,
    val productId: String,
    val qty: Int = 1
)

@Serializable
data class UpdateCartItemRequest(
    val productId: String,
    val qty: Int          // 0 = eliminar del carrito
)

// ─── PROMOCIONES / DESCUENTOS ────────────────────────────────
@Serializable
enum class PromotionType {
    PERCENTAGE,           // Descuento porcentual (ej: 20%)
    FIXED_AMOUNT,         // Descuento fijo (ej: $2.000)
    HAPPY_HOUR            // Oferta por rango de hora
}

@Serializable
data class Promotion(
    val id: String = "",
    val bakeryId: String = "",
    val productId: String = "",   // Vacío = aplica a toda la orden
    val title: String = "",
    val description: String = "",
    val type: PromotionType = PromotionType.PERCENTAGE,
    val discountPct: Double = 0.0,          // Para PERCENTAGE
    val discountAmount: Double = 0.0,       // Para FIXED_AMOUNT
    val happyHourStart: String = "",        // "HH:mm" para HAPPY_HOUR
    val happyHourEnd: String = "",          // "HH:mm" para HAPPY_HOUR
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class CreatePromotionRequest(
    val productId: String = "",
    val title: String,
    val description: String = "",
    val type: PromotionType,
    val discountPct: Double = 0.0,
    val discountAmount: Double = 0.0,
    val happyHourStart: String = "",
    val happyHourEnd: String = ""
)

// ─── FIDELIZACIÓN / SELLOS ───────────────────────────────────
@Serializable
data class LoyaltyCard(
    val id: String = "",
    val userId: String = "",
    val bakeryId: String = "",
    val stamps: Int = 0,              // Sellos acumulados
    val stampsForReward: Int = 9,     // Cada cuántos sellos se gana una recompensa
    val totalRewardsEarned: Int = 0,  // Recompensas totales ganadas históricamente
    val freeItemsAvailable: Int = 0,  // Recompensas pendientes de usar
    val updatedAt: Long = System.currentTimeMillis()
)

// ─── ESTADÍSTICAS DEL BAKER ──────────────────────────────────
@Serializable
data class BakeryStats(
    val bakeryId: String = "",
    val totalOrders: Int = 0,
    val totalRevenue: Double = 0.0,
    val averageOrderValue: Double = 0.0,
    val ordersToday: Int = 0,
    val revenueToday: Double = 0.0,
    val ordersThisWeek: Int = 0,
    val revenueThisWeek: Double = 0.0,
    val topProducts: List<ProductStat> = emptyList(),
    val ordersByHour: Map<String, Int> = emptyMap(),    // "08" -> 12 órdenes
    val ordersByStatus: Map<String, Int> = emptyMap()
)

@Serializable
data class ProductStat(
    val productId: String,
    val productName: String,
    val emoji: String,
    val totalSold: Int,
    val totalRevenue: Double
)

// ─── ADMIN STATS ─────────────────────────────────────────────
@Serializable
data class AdminStats(
    val totalBakeries: Int = 0,
    val activeBakeries: Int = 0,
    val totalUsers: Int = 0,
    val totalOrders: Int = 0,
    val totalRevenue: Double = 0.0,
    val ordersToday: Int = 0,
    val revenueToday: Double = 0.0,
    val topBakeries: List<BakeryStat> = emptyList()
)

@Serializable
data class BakeryStat(
    val bakeryId: String,
    val bakeryName: String,
    val totalOrders: Int,
    val totalRevenue: Double,
    val rating: Double
)

// ─── BÚSQUEDA ────────────────────────────────────────────────
@Serializable
data class SearchResult(
    val bakeries: List<Bakery> = emptyList(),
    val products: List<ProductSearchResult> = emptyList()
)

@Serializable
data class ProductSearchResult(
    val product: Product,
    val bakery: Bakery,
    val distanceKm: Double? = null
)

// ─── ORDEN: agregar notas ─────────────────────────────────────
// (El campo notes se suma a CreateOrderRequest — se hace vía extensión del modelo existente
//  para no romper retrocompatibilidad. Ver CreateOrderRequest con notes abajo)

// ─── TIEMPO ESTIMADO ─────────────────────────────────────────
@Serializable
data class SetEstimatedReadyRequest(
    val estimatedReadyAt: Long    // epoch millis
)
