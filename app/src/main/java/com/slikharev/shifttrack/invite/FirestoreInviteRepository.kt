package com.slikharev.shifttrack.invite

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.slikharev.shifttrack.data.remote.InviteDocument
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreInviteRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : InviteRepository {

    override suspend fun createInvite(hostUid: String, hostDisplayName: String): String {
        val token = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        firestore.collection(INVITES).document(token).set(
            mapOf(
                "token" to token,
                "hostUid" to hostUid,
                "hostDisplayName" to hostDisplayName,
                "createdAt" to now,
                "expiresAt" to (now + TimeUnit.DAYS.toMillis(VALIDITY_DAYS)),
                "claimed" to false,
                "claimedBy" to null,
            )
        ).await()
        return token
    }

    override suspend fun getInvite(token: String): InviteDocument? {
        val snap = firestore.collection(INVITES).document(token).get().await()
        if (!snap.exists()) return null
        return InviteDocument(
            token = token,
            hostUid = snap.getString("hostUid") ?: "",
            hostDisplayName = snap.getString("hostDisplayName") ?: "",
            createdAt = snap.getLong("createdAt") ?: 0L,
            expiresAt = snap.getLong("expiresAt") ?: Long.MAX_VALUE,
            claimed = snap.getBoolean("claimed") ?: false,
            claimedBy = snap.getString("claimedBy"),
        )
    }

    override suspend fun redeemInvite(token: String, guestUid: String): RedeemResult {
        return try {
            firestore.runTransaction<RedeemResult> { tx ->
                val inviteRef = firestore.collection(INVITES).document(token)
                val snap = tx.get(inviteRef)
                if (!snap.exists()) return@runTransaction RedeemResult.NotFound

                val claimed = snap.getBoolean("claimed") ?: false
                val expiresAt = snap.getLong("expiresAt") ?: 0L
                val hostUid = snap.getString("hostUid") ?: ""

                when {
                    claimed -> RedeemResult.AlreadyClaimed
                    System.currentTimeMillis() > expiresAt -> RedeemResult.Expired
                    else -> {
                        // Mark invite as claimed
                        tx.update(inviteRef, "claimed", true, "claimedBy", guestUid)
                        // Add viewer to host's spectators (merge so the doc is created if absent)
                        val userRef = firestore.collection(USERS).document(hostUid)
                        tx.set(
                            userRef,
                            mapOf("spectators" to FieldValue.arrayUnion(guestUid)),
                            SetOptions.merge(),
                        )
                        RedeemResult.Success
                    }
                }
            }.await()
        } catch (e: Exception) {
            RedeemResult.Error(e.message ?: "Redemption failed")
        }
    }

    companion object {
        private const val INVITES = "invites"
        private const val USERS = "users"
        private const val VALIDITY_DAYS = 7L
    }
}
