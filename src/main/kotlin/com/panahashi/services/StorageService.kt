package com.panahashi.services

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.firebase.cloud.StorageClient
import java.util.UUID

object StorageService {

    private val bucket get() = StorageClient.getInstance().bucket()

    /**
     * Sube un archivo a Firebase Storage y retorna la URL pública.
     *
     * @param bytes       contenido del archivo
     * @param contentType MIME type (ej: "image/jpeg", "image/png", "image/webp")
     * @param folder      carpeta dentro del bucket (ej: "products", "bakeries/logos")
     * @return URL pública permanente del archivo
     */
    suspend fun uploadImage(
        bytes: ByteArray,
        contentType: String,
        folder: String
    ): String {
        validateImageType(contentType)
        validateImageSize(bytes)

        val extension = extensionFromMime(contentType)
        val filename = "${UUID.randomUUID()}.$extension"
        val path = "$folder/$filename"

        val blob = bucket.create(path, bytes, contentType)

        // Hacer el blob público y retornar la URL
        blob.createAcl(com.google.cloud.storage.Acl.of(
            com.google.cloud.storage.Acl.User.ofAllUsers(),
            com.google.cloud.storage.Acl.Role.READER
        ))

        val bucketName = bucket.name
        return "https://storage.googleapis.com/$bucketName/$path"
    }

    /**
     * Elimina un archivo de Firebase Storage dado su URL pública.
     * Útil cuando se reemplaza una foto existente.
     */
    suspend fun deleteImage(publicUrl: String) {
        runCatching {
            // Extraer el path del blob desde la URL pública
            // URL format: https://storage.googleapis.com/{bucket}/{path}
            val bucketName = bucket.name
            val prefix = "https://storage.googleapis.com/$bucketName/"
            if (!publicUrl.startsWith(prefix)) return

            val blobPath = publicUrl.removePrefix(prefix)
            bucket.get(blobPath)?.delete()
        }
        // Si falla (archivo no existe, etc.) lo ignoramos silenciosamente
    }

    // ─── Validaciones ─────────────────────────────────────────

    private fun validateImageType(contentType: String) {
        val allowed = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")
        if (contentType !in allowed) {
            throw IllegalArgumentException(
                "Tipo de archivo no permitido: $contentType. Permitidos: jpeg, png, webp"
            )
        }
    }

    private fun validateImageSize(bytes: ByteArray) {
        val maxSizeBytes = 5 * 1024 * 1024 // 5 MB
        if (bytes.size > maxSizeBytes) {
            throw IllegalArgumentException(
                "La imagen es demasiado grande (${bytes.size / 1024 / 1024}MB). Máximo: 5MB"
            )
        }
    }

    private fun extensionFromMime(contentType: String): String = when (contentType) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
}
