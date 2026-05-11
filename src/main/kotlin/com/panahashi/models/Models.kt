package com.panahashi.models

import com.google.firebase.auth.EmailIdentifier
import kotlinx.serialization.Serializable

// ─── PRODUCTO ────────────────────────────────────────────────
@Serializable
data class Product(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val emoji: String = "",
    val status: String = "Ready now",
    val stock: Int = 0,
    val category: String = "bread",
    val description: String = "",
    val imageUrl: String = "",
    val available: Boolean = true
)

@Serializable
data class CreateProductRequest(
    val name: String,
    val price: Double,
    val emoji: String,
    val status: String = "Ready now",
    val stock: Int = 0,
    val category: String = "bread",
    val description: String = "",
    val imageUrl: String = "",
    val available: Boolean = true
)

@Serializable
data class UpdateStockRequest(
    val stock: Int
)

// ─── CARRITO ─────────────────────────────────────────────────
@Serializable
data class CartItem(
    val productId: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val emoji: String = "",
    val qty: Int = 1,
    val pickupTime: String = ""
)

// ─── ORDEN ───────────────────────────────────────────────────
@Serializable
data class Order(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val items: List<CartItem> = emptyList(),
    val total: Double = 0.0,
    val status: OrderStatus = OrderStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val pickupTime: String = "",
    val qrCode: String = ""
)

@Serializable
enum class OrderStatus {
    PENDING,
    CONFIRMED,
    READY,
    COMPLETED,
    CANCELLED
}

@Serializable
data class CreateOrderRequest(
    val items: List<CartItem>,
    val pickupTime: String
)

@Serializable
data class UpdateOrderStatusRequest(
    val status: OrderStatus
)

// ─── USUARIO ─────────────────────────────────────────────────
@Serializable
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class UpdateProfileRequest(
    val displayName: String? = null,
    val phone: String? = null
)

@Serializable
data class RegisterRequest(
    val displayName: String,
    val phone: String = ""
)
// ─── RESPUESTAS ──────────────────────────────────────────────
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null
)
