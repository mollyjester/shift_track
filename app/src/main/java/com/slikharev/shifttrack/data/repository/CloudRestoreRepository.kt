package com.slikharev.shifttrack.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.auth.requireUserId
import com.slikharev.shifttrack.data.local.db.dao.LeaveBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.LeaveDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeBalanceDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeDao
import com.slikharev.shifttrack.data.local.db.dao.ShiftDao
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.LeaveEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeEntity
import com.slikharev.shifttrack.data.local.db.entity.ShiftEntity
import com.slikharev.shifttrack.model.LeaveType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls all user data from Firestore subcollections back into Room.
 *
 * Used for disaster recovery when local data has been lost (e.g. destructive
 * database migration). All restored entities are marked `synced = true` to
 * avoid the SyncWorker re-pushing them.
 */
@Singleton
class CloudRestoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val shiftDao: ShiftDao,
    private val leaveDao: LeaveDao,
    private val overtimeDao: OvertimeDao,
    private val leaveBalanceDao: LeaveBalanceDao,
    private val overtimeBalanceDao: OvertimeBalanceDao,
    private val userSession: UserSession,
) {
    data class RestoreResult(
        val shiftsRestored: Int,
        val leavesRestored: Int,
        val overtimeRestored: Int,
    )

    suspend fun restore(): RestoreResult {
        val uid = userSession.requireUserId()

        val shifts = restoreShifts(uid)
        val leaves = restoreLeaves(uid)
        val overtime = restoreOvertime(uid)

        recalculateLeaveBalances(uid)
        recalculateOvertimeBalances(uid)

        return RestoreResult(
            shiftsRestored = shifts,
            leavesRestored = leaves,
            overtimeRestored = overtime,
        )
    }

    private suspend fun restoreShifts(uid: String): Int {
        val docs = fetchAll(uid, "shifts")
        val entities = docs.mapNotNull { doc ->
            val date = doc.getString("date") ?: return@mapNotNull null
            val shiftType = doc.getString("shiftType") ?: return@mapNotNull null
            ShiftEntity(
                date = date,
                shiftType = shiftType,
                isManualOverride = doc.getBoolean("isManualOverride") ?: false,
                note = doc.getString("note"),
                userId = uid,
                synced = true,
            )
        }
        entities.forEach { shiftDao.upsert(it) }
        return entities.size
    }

    private suspend fun restoreLeaves(uid: String): Int {
        val docs = fetchAll(uid, "leaves")
        val entities = docs.mapNotNull { doc ->
            val date = doc.getString("date") ?: return@mapNotNull null
            val leaveType = doc.getString("leaveType") ?: return@mapNotNull null
            val parsedType = LeaveType.fromString(leaveType) ?: return@mapNotNull null
            LeaveEntity(
                date = date,
                leaveType = parsedType.name,
                halfDay = doc.getBoolean("halfDay") ?: false,
                note = doc.getString("note"),
                userId = uid,
                synced = true,
            )
        }
        entities.forEach { leaveDao.upsert(it) }
        return entities.size
    }

    private suspend fun restoreOvertime(uid: String): Int {
        val docs = fetchAll(uid, "overtime")
        val entities = docs.mapNotNull { doc ->
            val date = doc.getString("date") ?: return@mapNotNull null
            val hours = doc.getDouble("hours")?.toFloat() ?: return@mapNotNull null
            OvertimeEntity(
                date = date,
                hours = hours,
                note = doc.getString("note"),
                userId = uid,
                synced = true,
            )
        }
        entities.forEach { overtimeDao.upsert(it) }
        return entities.size
    }

    private suspend fun fetchAll(uid: String, collection: String): List<com.google.firebase.firestore.DocumentSnapshot> {
        val result = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
        var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
        do {
            var query = firestore
                .collection("users").document(uid)
                .collection(collection)
                .limit(PAGE_SIZE.toLong())
            if (lastDoc != null) {
                query = query.startAfter(lastDoc)
            }
            val snapshot: QuerySnapshot = query.get().await()
            result.addAll(snapshot.documents)
            lastDoc = snapshot.documents.lastOrNull()
        } while (snapshot.documents.size == PAGE_SIZE)
        return result
    }

    private suspend fun recalculateLeaveBalances(uid: String) {
        val currentYear = LocalDate.now().year
        for (year in (currentYear - 1)..currentYear) {
            val start = LocalDate.of(year, 1, 1).toString()
            val end = LocalDate.of(year, 12, 31).toString()
            val leaveTypes = LeaveType.entries.map { it.name }
            for (type in leaveTypes) {
                val usedDays = leaveDao.sumLeaveDaysByType(uid, start, end, type).first()
                if (usedDays > 0f) {
                    val existing = leaveBalanceDao.getBalanceForYearAndType(uid, year, type)
                    if (existing == null) {
                        leaveBalanceDao.upsert(
                            LeaveBalanceEntity(
                                year = year,
                                leaveType = type,
                                totalDays = 0f,
                                usedDays = usedDays,
                                userId = uid,
                            ),
                        )
                    } else {
                        leaveBalanceDao.update(existing.copy(usedDays = usedDays))
                    }
                }
            }
        }
    }

    private suspend fun recalculateOvertimeBalances(uid: String) {
        val currentYear = LocalDate.now().year
        for (year in (currentYear - 1)..currentYear) {
            val start = LocalDate.of(year, 1, 1).toString()
            val end = LocalDate.of(year, 12, 31).toString()
            val totalHours = overtimeDao.sumOvertimeHoursOnce(uid, start, end)
            if (totalHours > 0f) {
                val existing = overtimeBalanceDao.getBalanceForYear(uid, year)
                if (existing == null) {
                    overtimeBalanceDao.upsert(
                        OvertimeBalanceEntity(year = year, totalHours = totalHours, userId = uid),
                    )
                } else {
                    overtimeBalanceDao.update(existing.copy(totalHours = totalHours))
                }
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 500
    }
}
