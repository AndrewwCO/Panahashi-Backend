package com.panahashi.services

import com.panahashi.models.*
import com.panahashi.services.Collections.ORDERS
import com.panahashi.services.Collections.BAKERIES
import com.panahashi.services.Collections.USERS
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object StatsService {

    private val zone = ZoneId.of("America/Bogota")

    // ─── Stats para el Baker ──────────────────────────────────

    suspend fun getBakeryStats(bakeryId: String): BakeryStats {
        val allOrders = FirestoreService.queryCollection(ORDERS, "bakeryId", bakeryId)
            .map { it.toSimpleOrder() }
            .filter { it.status != OrderStatus.CANCELLED }

        val now = Instant.now().atZone(zone)
        val startOfToday = now.truncatedTo(ChronoUnit.DAYS).toInstant().toEpochMilli()
        val startOfWeek  = now.minusDays(now.dayOfWeek.value.toLong() - 1)
            .truncatedTo(ChronoUnit.DAYS).toInstant().toEpochMilli()

        val todayOrders    = allOrders.filter { it.createdAt >= startOfToday }
        val thisWeekOrders = allOrders.filter { it.createdAt >= startOfWeek }

        // Productos más vendidos
        val productCounts = mutableMapOf<String, Triple<String, String, Int>>()   // id → (name, emoji, qty)
        val productRevenue = mutableMapOf<String, Double>()
        allOrders.flatMap { it.items }.forEach { item ->
            val current = productCounts[item.productId]
            productCounts[item.productId] = Triple(
                item.name,
                item.emoji,
                (current?.third ?: 0) + item.qty
            )
            productRevenue[item.productId] = (productRevenue[item.productId] ?: 0.0) + (item.price * item.qty)
        }

        val topProducts = productCounts.entries
            .sortedByDescending { it.value.third }
            .take(5)
            .map { (id, triple) ->
                ProductStat(
                    productId     = id,
                    productName   = triple.first,
                    emoji         = triple.second,
                    totalSold     = triple.third,
                    totalRevenue  = productRevenue[id] ?: 0.0
                )
            }

        // Pedidos por hora
        val ordersByHour = allOrders
            .groupBy { order ->
                Instant.ofEpochMilli(order.createdAt).atZone(zone).hour.toString().padStart(2, '0')
            }
            .mapValues { (_, orders) -> orders.size }

        // Pedidos por estado
        val ordersByStatus = allOrders.groupBy { it.status.name }.mapValues { it.value.size }

        return BakeryStats(
            bakeryId          = bakeryId,
            totalOrders       = allOrders.size,
            totalRevenue      = allOrders.sumOf { it.total },
            averageOrderValue = if (allOrders.isEmpty()) 0.0 else allOrders.sumOf { it.total } / allOrders.size,
            ordersToday       = todayOrders.size,
            revenueToday      = todayOrders.sumOf { it.total },
            ordersThisWeek    = thisWeekOrders.size,
            revenueThisWeek   = thisWeekOrders.sumOf { it.total },
            topProducts       = topProducts,
            ordersByHour      = ordersByHour,
            ordersByStatus    = ordersByStatus
        )
    }

    // ─── Stats globales para Admin ────────────────────────────

    suspend fun getAdminStats(): AdminStats {
        val allOrders   = FirestoreService.getCollection(ORDERS).map { it.toSimpleOrder() }
        val allBakeries = FirestoreService.getCollection(BAKERIES)
        val allUsers    = FirestoreService.getCollection(USERS)

        val now = Instant.now().atZone(zone)
        val startOfToday = now.truncatedTo(ChronoUnit.DAYS).toInstant().toEpochMilli()

        val completedOrders = allOrders.filter { it.status != OrderStatus.CANCELLED }
        val todayOrders     = completedOrders.filter { it.createdAt >= startOfToday }

        // Top panaderías por ingresos
        val revenueByBakery = completedOrders.groupBy { it.bakeryId }
            .mapValues { (_, orders) -> orders.sumOf { it.total } }

        val topBakeries = revenueByBakery.entries
            .sortedByDescending { it.value }
            .take(10)
            .mapNotNull { (bakeryId, revenue) ->
                runCatching {
                    val bakery = BakeryService.getBakeryById(bakeryId)
                    BakeryStat(
                        bakeryId     = bakeryId,
                        bakeryName   = bakery.name,
                        totalOrders  = completedOrders.count { it.bakeryId == bakeryId },
                        totalRevenue = revenue,
                        rating       = bakery.rating
                    )
                }.getOrNull()
            }

        val activeBakeriesCount = allBakeries.count {
            it.getString("status") == BakeryStatus.ACTIVE.name
        }

        return AdminStats(
            totalBakeries   = allBakeries.size,
            activeBakeries  = activeBakeriesCount,
            totalUsers      = allUsers.size,
            totalOrders     = completedOrders.size,
            totalRevenue    = completedOrders.sumOf { it.total },
            ordersToday     = todayOrders.size,
            revenueToday    = todayOrders.sumOf { it.total },
            topBakeries     = topBakeries
        )
    }

    // ─── Búsqueda de productos ────────────────────────────────

    suspend fun searchProducts(
        query: String,
        lat: Double? = null,
        lng: Double? = null,
        radiusKm: Double = 10.0,
        category: String? = null
    ): SearchResult {
        val q = query.trim().lowercase()

        // Buscar panaderías que coincidan por nombre
        val bakeries = BakeryService.getActiveBakeries()
            .filter { it.name.lowercase().contains(q) }

        // Buscar productos que coincidan por nombre o categoría
        val allProducts = FirestoreService.getCollection(Collections.PRODUCTS)
            .map { it to it.toProductSimple() }
            .filter { (_, p) ->
                p.available && (
                    p.name.lowercase().contains(q) ||
                    p.category.lowercase().contains(q) ||
                    (category != null && p.category.lowercase() == category.lowercase())
                )
            }

        // Filtrar por distancia si se da ubicación
        val productResults = allProducts.mapNotNull { (_, product) ->
            val bakery = runCatching { BakeryService.getBakeryById(product.bakeryId) }.getOrNull()
                ?: return@mapNotNull null
            if (bakery.status != BakeryStatus.ACTIVE) return@mapNotNull null

            val distance: Double? = if (lat != null && lng != null) {
                haversineKm(lat, lng, bakery.lat, bakery.lng).also { d ->
                    if (d > radiusKm) return@mapNotNull null
                }
            } else null

            ProductSearchResult(product = product, bakery = bakery, distanceKm = distance)
        }.sortedBy { it.distanceKm ?: Double.MAX_VALUE }

        return SearchResult(bakeries = bakeries, products = productResults)
    }

    // ─── Helpers ─────────────────────────────────────────────

    private data class SimpleOrder(
        val bakeryId: String,
        val total: Double,
        val status: OrderStatus,
        val createdAt: Long,
        val items: List<CartItem>
    )

    @Suppress("UNCHECKED_CAST")
    private fun com.google.cloud.firestore.DocumentSnapshot.toSimpleOrder(): SimpleOrder {
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
        return SimpleOrder(
            bakeryId  = getString("bakeryId") ?: "",
            total     = getDouble("total")    ?: 0.0,
            status    = OrderStatus.valueOf(getString("status") ?: "PENDING"),
            createdAt = getLong("createdAt")  ?: 0L,
            items     = items
        )
    }

    private fun com.google.cloud.firestore.DocumentSnapshot.toProductSimple() = Product(
        id                 = id,
        bakeryId           = getString("bakeryId")    ?: "",
        name               = getString("name")        ?: "",
        price              = getDouble("price")       ?: 0.0,
        emoji              = getString("emoji")       ?: "",
        availabilityStatus = runCatching {
            ProductAvailabilityStatus.valueOf(getString("availabilityStatus") ?: "READY_NOW")
        }.getOrDefault(ProductAvailabilityStatus.READY_NOW),
        stock              = getLong("stock")?.toInt() ?: 0,
        category           = getString("category")    ?: "bread",
        description        = getString("description") ?: "",
        imageUrl           = getString("imageUrl")    ?: "",
        available          = getBoolean("available")  ?: true,
        advanceMinutes     = getLong("advanceMinutes")?.toInt() ?: 0
    )

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2).let { it * it }
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}
