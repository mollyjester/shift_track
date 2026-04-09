package com.slikharev.shifttrack.data.remote

import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles uploading and deleting attachment files in Firebase Storage.
 *
 * Storage path layout: `users/{uid}/attachments/{date}/{fileName}`
 */
@Singleton
class FirebaseStorageSyncDataSource @Inject constructor(
    private val storage: FirebaseStorage,
) {
    /**
     * Uploads [localFile] to Firebase Storage at [storagePath] and returns
     * the storage path for later reference.
     */
    suspend fun uploadFile(uid: String, localFile: File, storagePath: String): String {
        val ref = storage.reference.child(storagePath)
        ref.putFile(android.net.Uri.fromFile(localFile)).await()
        return storagePath
    }

    /** Deletes a file from Firebase Storage by its path. */
    suspend fun deleteFile(storagePath: String) {
        val ref = storage.reference.child(storagePath)
        runCatching { ref.delete().await() }
    }
}
