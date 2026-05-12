package com.panahashi.config

import com.panahashi.routes.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        healthRoutes()
        route("/api/v1") {
            // ── Existentes ────────────────────────────────────────────
            bakeryRoutes()
            productRoutes()
            orderRoutes()
            userRoutes()
            reviewRoutes()
            uploadRoutes()

            // ── Nuevos ────────────────────────────────────────────────
            favoriteRoutes()     // Favoritos de panaderías
            cartRoutes()         // Carrito persistente
            promotionRoutes()    // Promociones y happy hour
            loyaltyRoutes()      // Tarjetas de fidelización (sellos)
            paymentRoutes()      // Pago simulado
            statsRoutes()        // Reportes baker + admin dashboard
            searchRoutes()       // Búsqueda global de productos
        }
    }
}
