package com.panahashi.services

import com.google.firebase.auth.FirebaseAuth
import com.panahashi.models.UpdateProfileRequest
import com.panahashi.models.UserProfile
import com.panahashi.models.UserRole
import com.panahashi.services.Collections.USERS

object UserService {

    suspend fun getOrCreateProfile(uid: String): UserProfile {
        val doc = FirestoreService.getDocument(USERS, uid)
        if (doc != null && doc.exists()) {
            return doc.toUserProfile()
        }

        // Si no existe, crear desde Firebase Auth con rol CUSTOMER por defecto
        val firebaseUser = FirebaseAuth.getInstance().getUser(uid)
        val profile = UserProfile(
            uid = uid,
            displayName = firebaseUser.displayName ?: "",
            email = firebaseUser.email ?: "",
            phone = firebaseUser.phoneNumber ?: "",
            role = UserRole.CUSTOMER.name,
            bakeryId = "",
            fcmToken = "",
            createdAt = System.currentTimeMillis()
        )
        FirestoreService.createDocument(USERS, uid, profileToMap(profile))
        return profile
    }

    suspend fun updateProfile(uid: String, request: UpdateProfileRequest): UserProfile {
        val updates = mutableMapOf<String, Any>()
        request.displayName?.let { updates["displayName"] = it }
        request.phone?.let { updates["phone"] = it }
        request.fcmToken?.let { updates["fcmToken"] = it }

        if (updates.isNotEmpty()) {
            FirestoreService.updateDocument(USERS, uid, updates)
        }
        return getOrCreateProfile(uid)
    }

    // Solo admin puede cambiar el rol de un usuario
    suspend fun updateRole(uid: String, newRole: UserRole): UserProfile {
        FirestoreService.updateDocument(USERS, uid, mapOf("role" to newRole.name))
        return getOrCreateProfile(uid)
    }

    suspend fun getUserById(uid: String): UserProfile {
        val doc = FirestoreService.getDocument(USERS, uid)
            ?: throw NoSuchElementException("Usuario $uid no encontrado")
        if (!doc.exists()) throw NoSuchElementException("Usuario $uid no encontrado")
        return doc.toUserProfile()
    }

    // ─── Helpers ─────────────────────────────────────────────
    private fun com.google.cloud.firestore.DocumentSnapshot.toUserProfile() = UserProfile(
        uid = id,
        displayName = getString("displayName") ?: "",
        email = getString("email") ?: "",
        phone = getString("phone") ?: "",
        role = getString("role") ?: UserRole.CUSTOMER.name,
        bakeryId = getString("bakeryId") ?: "",
        fcmToken = getString("fcmToken") ?: "",
        createdAt = getLong("createdAt") ?: 0L
    )

    private fun profileToMap(profile: UserProfile): Map<String, Any> = mapOf(
        "uid" to profile.uid,
        "displayName" to profile.displayName,
        "email" to profile.email,
        "phone" to profile.phone,
        "role" to profile.role,
        "bakeryId" to profile.bakeryId,
        "fcmToken" to profile.fcmToken,
        "createdAt" to profile.createdAt
    )
}