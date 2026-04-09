package com.slikharev.shifttrack.data.remote

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreUserDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    /** Creates or merges the user document in users/{uid}. */
    suspend fun saveUser(user: FirebaseUser) {
        val doc = mapOf(
            "uid" to user.uid,
            "displayName" to (user.displayName ?: ""),
            "email" to (user.email ?: ""),
        )
        firestore.collection("users")
            .document(user.uid)
            .set(doc, SetOptions.merge())
            .await()
    }

    /** Writes the FCM registration token to users/{uid}.fcmToken. */
    suspend fun saveFcmToken(uid: String, token: String) {
        firestore.collection("users")
            .document(uid)
            .set(mapOf("fcmToken" to token), SetOptions.merge())
            .await()
    }

    /** Persists the anchor date and cycle index in the user's Firestore doc. */
    suspend fun saveAnchor(uid: String, anchorDate: String, anchorCycleIndex: Int) {
        firestore.collection("users")
            .document(uid)
            .set(
                mapOf("anchorDate" to anchorDate, "anchorCycleIndex" to anchorCycleIndex),
                SetOptions.merge(),
            )
            .await()
    }

    /**
     * Deletes all Firestore data owned by [uid]: sub-collections
     * (shifts, leaves, overtime) and the root user document.
     * Called as part of account deletion.
     */
    suspend fun deleteUserData(uid: String) {
        val userRef = firestore.collection("users").document(uid)
        for (sub in listOf("shifts", "leaves", "overtime")) {
            while (true) {
                val snapshot = userRef.collection(sub).limit(500).get().await()
                if (snapshot.isEmpty) break
                val batch = firestore.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        userRef.delete().await()
    }
}
