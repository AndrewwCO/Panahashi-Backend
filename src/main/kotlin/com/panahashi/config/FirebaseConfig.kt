package com.panahashi.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.ktor.server.application.*
import org.slf4j.LoggerFactory
import java.io.FileInputStream

private val logger = LoggerFactory.getLogger("FirebaseConfig")

fun Application.configureFirebase() {
    val serviceAccountPath = environment.config.propertyOrNull("firebase.serviceAccountPath")?.getString()
        ?: System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        ?: "serviceAccountKey.json"

    val credentials = try {
        FileInputStream(serviceAccountPath).use { stream ->
            GoogleCredentials.fromStream(stream)
        }
    } catch (e: Exception) {
        logger.warn("serviceAccountKey.json no encontrado. Usando credenciales del entorno: ${e.message}")
        GoogleCredentials.getApplicationDefault()
    }

    val options = FirebaseOptions.builder()
        .setCredentials(credentials)
        .setDatabaseUrl(
            environment.config.propertyOrNull("firebase.databaseUrl")?.getString()
                ?: System.getenv("FIREBASE_DATABASE_URL")
                ?: "https://panahashi-default-rtdb.firebaseio.com"
        )
        // ← NUEVO: bucket de Firebase Storage (formato: tu-proyecto.appspot.com)
        .setStorageBucket(
            environment.config.propertyOrNull("firebase.storageBucket")?.getString()
                ?: System.getenv("FIREBASE_STORAGE_BUCKET")
                ?: "panahashi.appspot.com"
        )
        .build()

    if (FirebaseApp.getApps().isEmpty()) {
        FirebaseApp.initializeApp(options)
        logger.info("✅ Firebase inicializado correctamente (Auth + Firestore + Storage + FCM)")
    }
}