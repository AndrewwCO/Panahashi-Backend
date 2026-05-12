package com.panahashi.routes

import com.panahashi.models.ApiResponse
import com.panahashi.services.FavoriteService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.favoriteRoutes() {
    route("/favorites") {

        authenticate("firebase-auth") {

            // GET /api/v1/favorites — mis panaderías favoritas
            get {
                val uid = call.principal<UserIdPrincipal>()!!.name
                val favorites = FavoriteService.getFavorites(uid)
                call.respond(ApiResponse(success = true, data = favorites))
            }

            // POST /api/v1/favorites/{bakeryId} — toggle favorito (agrega o quita)
            post("{bakeryId}") {
                val uid      = call.principal<UserIdPrincipal>()!!.name
                val bakeryId = call.parameters["bakeryId"]
                    ?: throw IllegalArgumentException("bakeryId requerido")
                val isFav = FavoriteService.toggleFavorite(uid, bakeryId)
                call.respond(ApiResponse(
                    success = true,
                    data    = mapOf("isFavorite" to isFav),
                    message = if (isFav) "Panadería agregada a favoritos" else "Panadería eliminada de favoritos"
                ))
            }

            // GET /api/v1/favorites/{bakeryId}/status — consultar si es favorita
            get("{bakeryId}/status") {
                val uid      = call.principal<UserIdPrincipal>()!!.name
                val bakeryId = call.parameters["bakeryId"]
                    ?: throw IllegalArgumentException("bakeryId requerido")
                val isFav = FavoriteService.isFavorite(uid, bakeryId)
                call.respond(ApiResponse(success = true, data = mapOf("isFavorite" to isFav)))
            }

            // DELETE /api/v1/favorites/{bakeryId} — eliminar favorito explícitamente
            delete("{bakeryId}") {
                val uid      = call.principal<UserIdPrincipal>()!!.name
                val bakeryId = call.parameters["bakeryId"]
                    ?: throw IllegalArgumentException("bakeryId requerido")
                FavoriteService.removeFavorite(uid, bakeryId)
                call.respond(ApiResponse<Unit>(success = true, message = "Eliminado de favoritos"))
            }
        }
    }
}
