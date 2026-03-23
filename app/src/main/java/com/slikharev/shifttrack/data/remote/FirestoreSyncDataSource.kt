package com.slikharev.shifttrack.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.slikharev.shifttrack.data.local.db.entity.LeaveEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeEntity
import com.slikharev.shifttrack.data.local.db.entity.ShiftEntity
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes local Room entities to Firestore under `users/{uid}/<collection>/{date}`.
 *
 * Firestore paths:
 *   users/{uid}/shifts/{date}     ← ShiftEntity
 *   users/{uid}/leaves/{date}     ← LeaveEntity
 *   users/{uid}/overtime/{date}   ← OvertimeEntity
 *
 * All writes use SetOptions.merge() so concurrent writes from multiple devices
 * are safe for independent day documents.
 */
@Singleton
class FirestoreSyncDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    /** Batch-writes a list of shift records for [uid], respecting Firestore's 500-op limit. */
    suspend fun syncShifts(uid: String, shifts: List<ShiftEntity>) {
        if (shifts.isEmpty()) return
        shifts.chunked(MAX_BATCH_SIZE).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { entity ->
                val ref = firestore
                    .collection("users").document(uid)
                    .collection("shifts").document(entity.date)
                batch.set(ref, entity.toMap(), SetOptions.merge())
            }
            batch.commit().await()
        }
    }

    /** Batch-writes a list of leave records for [uid], respecting Firestore's 500-op limit. */
    suspend fun syncLeaves(uid: String, leaves: List<LeaveEntity>) {
        if (leaves.isEmpty()) return
        leaves.chunked(MAX_BATCH_SIZE).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { entity ->
                val ref = firestore
                    .collection("users").document(uid)
                    .collection("leaves").document(entity.date)
                batch.set(ref, entity.toMap(), SetOptions.merge())
            }
            batch.commit().await()
        }
    }

    /** Batch-writes a list of overtime records for [uid], respecting Firestore's 500-op limit. */
    suspend fun syncOvertimes(uid: String, overtimes: List<OvertimeEntity>) {
        if (overtimes.isEmpty()) return
        overtimes.chunked(MAX_BATCH_SIZE).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { entity ->
                val ref = firestore
                    .collection("users").document(uid)
                    .collection("overtime").document(entity.date)
                batch.set(ref, entity.toMap(), SetOptions.merge())
            }
            batch.commit().await()
        }
    }

    private companion object {
        const val MAX_BATCH_SIZE = 500
    }

    // ── Serialisation helpers ─────────────────────────────────────────────────

    private fun ShiftEntity.toMap(): Map<String, Any?> = mapOf(
        "date" to date,
        "shiftType" to shiftType,
        "isManualOverride" to isManualOverride,
        "note" to note,
        "userId" to userId,
    )

    private fun LeaveEntity.toMap(): Map<String, Any?> = mapOf(
        "date" to date,
        "leaveType" to leaveType,
        "halfDay" to halfDay,
        "note" to note,
        "userId" to userId,
    )

    private fun OvertimeEntity.toMap(): Map<String, Any?> = mapOf(
        "date" to date,
        "hours" to hours,
        "note" to note,
        "userId" to userId,
    )
}
