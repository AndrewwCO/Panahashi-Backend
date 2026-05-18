package com.panahashi.services

import com.panahashi.models.*
import com.panahashi.services.Collections.BAKERIES
import com.panahashi.services.Collections.USERS
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.*
import java.time.ZoneId

object BakeryService {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // ─── CRUD ────────────────────────────────────────────────

    suspend fun createBakery(request: CreateBakeryRequest): Bakery {
        val ownerDoc = FirestoreService.getDocument(USERS, request.ownerId)
            ?: throw IllegalArgumentException("Usuario ${request.ownerId} no existe")
        val ownerRole = ownerDoc.getString("role") ?: UserRole.CUSTOMER.name
        if (ownerRole != UserRole.BAKER.name) {
            throw IllegalArgumentException("El usuario ${request.ownerId} no tiene rol BAKER. Cambia su rol primero.")
        }

        val data = mapOf(
            "name"         to request.name,
            "description"  to request.description,
            "address"      to request.address,
            "lat"          to request.lat,
            "lng"          to request.lng,
            "phone"        to request.phone,
            "openTime"     to request.openTime,
            "closeTime"    to request.closeTime,
            "logoUrl"      to "",
            "bannerUrl"    to "",
            "isOpen"       to false,
            "rating"       to 0.0,
            "totalReviews" to 0,
            "status"       to BakeryStatus.ACTIVE.name,
            "ownerId"      to request.ownerId,
            "createdAt"    to System.currentTimeMillis()
        )

        val bakeryId = FirestoreService.createDocumentAutoId(BAKERIES, data)
        FirestoreService.updateDocument(USERS, request.ownerId, mapOf("bakeryId" to bakeryId))

        return getBakeryById(bakeryId)
    }

    suspend fun getBakeryById(id: String): Bakery {
        val doc = FirestoreService.getDocument(BAKERIES, id)
            ?: throw NoSuchElementException("Panadería $id no encontrada")
        if (!doc.exists()) throw NoSuchElementException("Panadería $id no encontrada")
        return doc.toBakery()
    }

    suspend fun getAllBakeries(): List<Bakery> {
        return FirestoreService.getCollection(BAKERIES)
            .map { it.toBakery() }
            .sortedBy { it.name }
    }

    suspend fun getActiveBakeries(): List<Bakery> {
        return FirestoreService.queryCollection(BAKERIES, "status", BakeryStatus.ACTIVE.name)
            .map { it.toBakery() }
    }

    suspend fun updateBakery(bakeryId: String, request: UpdateBakeryRequest): Bakery {
        if (!FirestoreService.exists(BAKERIES, bakeryId))
            throw NoSuchElementException("Panadería $bakeryId no encontrada")

        val updates = mutableMapOf<String, Any>()
        request.name?.let        { updates["name"]        = it }
        request.description?.let { updates["description"] = it }
        request.address?.let     { updates["address"]     = it }
        request.lat?.let         { updates["lat"]         = it }
        request.lng?.let         { updates["lng"]         = it }
        request.phone?.let       { updates["phone"]       = it }
        request.logoUrl?.let     { updates["logoUrl"]     = it }
        request.bannerUrl?.let   { updates["bannerUrl"]   = it }
        request.isOpen?.let      { updates["isOpen"]      = it }
        request.openTime?.let    { updates["openTime"]    = it }
        request.closeTime?.let   { updates["closeTime"]   = it }
        request.status?.let      { updates["status"]      = it.name }

        if (updates.isNotEmpty()) {
            FirestoreService.updateDocument(BAKERIES, bakeryId, updates)
        }
        return getBakeryById(bakeryId)
    }

    suspend fun deleteBakery(bakeryId: String) {
        val bakery = getBakeryById(bakeryId)
        if (bakery.ownerId.isNotEmpty()) {
            FirestoreService.updateDocument(USERS, bakery.ownerId, mapOf("bakeryId" to ""))
        }
        FirestoreService.deleteDocument(BAKERIES, bakeryId)
    }

    // ─── GEO: panaderías cercanas ────────────────────────────
    // FIX: corregido el typo "getNearbBakeries" → "getNearbyBakeries"
    // TODO: para escala mayor, migrar a GeoHash o Algolia GeoSearch
    suspend fun getNearbyBakeries(lat: Double, lng: Double, radiusKm: Double): List<BakeryWithDistance> {
        val active = getActiveBakeries()
        return active
            .map { bakery ->
                val distance = haversineKm(lat, lng, bakery.lat, bakery.lng)
                BakeryWithDistance(bakery, distance)
            }
            .filter { it.distanceKm <= radiusKm }
            .sortedBy { it.distanceKm }
    }

    // ─── Toggle isOpen con validación de horario ─────────────
    // FIX: ahora valida que la hora actual esté dentro del horario configurado
    suspend fun toggleOpen(bakeryId: String, isOpen: Boolean): Bakery {
        if (isOpen) {
            val bakery = getBakeryById(bakeryId)
            val now = LocalTime.now(ZoneId.of("America/Bogota"))
            val open  = runCatching { LocalTime.parse(bakery.openTime,  timeFormatter) }.getOrNull()
            val close = runCatching { LocalTime.parse(bakery.closeTime, timeFormatter) }.getOrNull()

            if (open != null && close != null && (now.isBefore(open) || now.isAfter(close))) {
                throw IllegalArgumentException(
                    "Fuera del horario configurado (${bakery.openTime}–${bakery.closeTime}). " +
                            "Puedes abrir igual editando isOpen directamente desde el panel admin."
                )
            }
        }
        FirestoreService.updateDocument(BAKERIES, bakeryId, mapOf("isOpen" to isOpen))
        return getBakeryById(bakeryId)
    }

    /**
     * Verifica si una hora de pickup está dentro del horario de la panadería.
     * Se usa al crear órdenes.
     */
    fun isPickupTimeValid(bakery: Bakery, pickupTime: String): Boolean {
        val pickup = runCatching { LocalTime.parse(pickupTime, timeFormatter) }.getOrNull()
            ?: return false
        val open   = runCatching { LocalTime.parse(bakery.openTime,  timeFormatter) }.getOrNull()
            ?: return true   // si no se puede parsear el horario, no bloqueamos
        val close  = runCatching { LocalTime.parse(bakery.closeTime, timeFormatter) }.getOrNull()
            ?: return true
        return !pickup.isBefore(open) && !pickup.isAfter(close)
    }

    // ─── Helpers ─────────────────────────────────────────────

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toBakery() = Bakery(
        id           = id,
        name         = getString("name")         ?: "",
        description  = getString("description")  ?: "",
        address      = getString("address")      ?: "",
        lat          = getDouble("lat")          ?: 0.0,
        lng          = getDouble("lng")          ?: 0.0,
        phone        = getString("phone")        ?: "",
        logoUrl      = getString("logoUrl")      ?: "",
        bannerUrl    = getString("bannerUrl")    ?: "",
        isOpen       = getBoolean("isOpen")      ?: false,
        openTime     = getString("openTime")     ?: "07:00",
        closeTime    = getString("closeTime")    ?: "14:00",
        rating       = getDouble("rating")       ?: 0.0,
        totalReviews = getLong("totalReviews")?.toInt() ?: 0,
        status       = BakeryStatus.valueOf(getString("status") ?: "ACTIVE"),
        ownerId      = getString("ownerId")      ?: "",
        createdAt    = getLong("createdAt")      ?: 0L
    )
}

@kotlinx.serialization.Serializable
data class BakeryWithDistance(
    val bakery: Bakery,
    val distanceKm: Double
)
