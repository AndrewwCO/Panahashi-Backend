package com.panahashi.services

import com.google.firebase.auth.FirebaseAuth
import com.panahashi.models.UpdateProfileRequest
import com.panahashi.models.UserProfile
import com.panahashi.services.Collections.USERS

object UserService {

    suspend fun getOrCreateProfile(uid: String): UserProfile {
        val doc = FirestoreService.getDocument(USERS, uid)
        if (doc != null && doc.exists()) {
            return doc.toUserProfile()
        }

        // Si no existe en Firestore, lo crea desde Firebase Auth
        val firebaseUser = FirebaseAuth.getInstance().getUser(uid)
        val profile = UserProfile(
            uid = uid,
            displayName = firebaseUser.displayName ?: "",
            email = firebaseUser.email ?: "",
            phone = firebaseUser.phoneNumber ?: "",
            createdAt = System.currentTimeMillis()
        )
        FirestoreService.createDocument(USERS, uid, profileToMap(profile))
        return profile
    }

    suspend fun updateProfile(uid: String, request: UpdateProfileRequest): UserProfile {
        val updates = mutableMapOf<String, Any>()
        request.displayName?.let { updates["displayName"] = it }
        request.phone?.let { updates["phone"] = it }

        if (updates.isNotEmpty()) {
            FirestoreService.updateDocument(USERS, uid, updates)
        }
        return getOrCreateProfile(uid)
    }

    // ─── Helpers ─────────────────────────────────────────────
    private fun com.google.cloud.firestore.DocumentSnapshot.toUserProfile() = UserProfile(
        uid = id,
        displayName = getString("displayName") ?: "",
        email = getString("email") ?: "",
        phone = getString("phone") ?: "",
        createdAt = getLong("createdAt") ?: 0L
    )

    private fun profileToMap(profile: UserProfile): Map<String, Any> = mapOf(
        "uid" to profile.uid,
        "displayName" to profile.displayName,
        "email" to profile.email,
        "phone" to profile.phone,
        "createdAt" to profile.createdAt
    )
}
