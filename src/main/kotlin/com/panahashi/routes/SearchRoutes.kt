package com.panahashi.routes

import com.panahashi.models.ApiResponse
import com.panahashi.services.StatsService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.searchRoutes() {
    route("/search") {

        // GET /api/v1/search?q=croissant&lat=4.71&lng=-74.07&radius=5&category=pastelería
        // Búsqueda pública de productos y panaderías por nombre o categoría.
        // lat, lng y radius son opcionales — sin ellos busca en toda la plataforma.
        get {
            val query    = call.request.queryParameters["q"]
                ?: throw IllegalArgumentException("Parámetro 'q' requerido")
            val lat      = call.request.queryParameters["lat"]?.toDoubleOrNull()
            val lng      = call.request.queryParameters["lng"]?.toDoubleOrNull()
            val radius   = call.request.queryParameters["radius"]?.toDoubleOrNull() ?: 10.0
            val category = call.request.queryParameters["category"]

            if (query.length < 2)
                throw IllegalArgumentException("La búsqueda debe tener al menos 2 caracteres")

            val results = StatsService.searchProducts(
                query    = query,
                lat      = lat,
                lng      = lng,
                radiusKm = radius,
                category = category
            )
            call.respond(ApiResponse(success = true, data = results))
        }
    }
}
