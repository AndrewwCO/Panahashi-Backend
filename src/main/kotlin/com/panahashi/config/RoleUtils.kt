package com.panahashi.config

import com.panahashi.models.UserRole
import com.panahashi.services.Collections.USERS
import com.panahashi.services.FirestoreService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*

/**
 * Obtiene el rol del usuario autenticado consultando Firestore.
 * Retorna null si el usuario no existe o no tiene rol definido (lo trata como CUSTOMER).
 */
suspend fun ApplicationCall.getUserRole(): UserRole {
    val uid = principal<UserIdPrincipal>()?.name ?: return UserRole.CUSTOMER
    val doc = FirestoreService.getDocument(USERS, uid) ?: return UserRole.CUSTOMER
    val roleStr = doc.getString("role") ?: return UserRole.CUSTOMER
    return runCatching { UserRole.valueOf(roleStr) }.getOrDefault(UserRole.CUSTOMER)
}

/**
 * Obtiene el bakeryId vinculado al baker autenticado.
 * Lanza excepción si no tiene panadería asignada.
 */
suspend fun ApplicationCall.getBakeryId(): String {
    val uid = principal<UserIdPrincipal>()?.name ?: throw IllegalStateException("No autenticado")
    val doc = FirestoreService.getDocument(USERS, uid) ?: throw IllegalStateException("Usuario no encontrado")
    val role = doc.getString("role") ?: UserRole.CUSTOMER.name
    if (role != UserRole.BAKER.name && role != UserRole.ADMIN.name)
        throw IllegalArgumentException("Acceso denegado: se requiere rol BAKER")
    return doc.getString("bakeryId")?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("Este usuario no tiene una panadería asignada")
}

/**
 * Verifica que el usuario sea ADMIN. Si no, responde 403 y retorna false.
 */
suspend fun ApplicationCall.requireAdmin(): Boolean {
    val role = getUserRole()
    if (role != UserRole.ADMIN) {
        respond(HttpStatusCode.Forbidden, com.panahashi.config.ApiError("FORBIDDEN", "Solo administradores pueden realizar esta acción"))
        return false
    }
    return true
}

/**
 * Verifica que el usuario sea BAKER. Si no, responde 403 y retorna false.
 */
suspend fun ApplicationCall.requireBaker(): Boolean {
    val role = getUserRole()
    if (role != UserRole.BAKER && role != UserRole.ADMIN) {
        respond(HttpStatusCode.Forbidden, com.panahashi.config.ApiError("FORBIDDEN", "Solo panaderías pueden realizar esta acción"))
        return false
    }
    return true
}
