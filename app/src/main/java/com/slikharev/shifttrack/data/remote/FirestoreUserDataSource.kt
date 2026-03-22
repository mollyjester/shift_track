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
}
