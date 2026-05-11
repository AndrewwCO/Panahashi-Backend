package com.panahashi.routes

import com.panahashi.models.ApiResponse
import com.panahashi.models.UpdateProfileRequest
import com.panahashi.services.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoutes() {
    route("/users") {

        authenticate("firebase-auth") {
            get("test-firebase") {
                try {
                    val db = com.google.firebase.cloud.FirestoreClient.getFirestore()

                    val data: Map<String, Any> = mapOf(
                        "test" to "funciona",
                        "timestamp" to System.currentTimeMillis()
                    )

                    db.collection("test").add(data)

                    call.respond("🔥 Firebase OK")
                } catch (e: Exception) {
                    call.respond("❌ Error: ${e.message}")
                }
            }
            // GET /api/v1/users/me — obtener perfil
            get("me") {
                val uid = call.principal<UserIdPrincipal>()!!.name
                val profile = UserService.getOrCreateProfile(uid)
                call.respond(ApiResponse(success = true, data = profile))
            }

            // PATCH /api/v1/users/me — actualizar perfil
            patch("me") {
                val uid = call.principal<UserIdPrincipal>()!!.name
                val request = call.receive<UpdateProfileRequest>()
                val profile = UserService.updateProfile(uid, request)
                call.respond(ApiResponse(success = true, data = profile))
            }
        }
    }
}
