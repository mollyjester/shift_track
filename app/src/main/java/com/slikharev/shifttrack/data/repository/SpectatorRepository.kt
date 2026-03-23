package com.slikharev.shifttrack.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.slikharev.shifttrack.engine.CadenceEngine
import com.slikharev.shifttrack.model.DayInfo
import com.slikharev.shifttrack.model.LeaveType
import com.slikharev.shifttrack.model.ShiftType
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a host user's shift/leave/overtime data from Firestore.
 * Used by spectators to view another user's calendar (read-only).
 */
@Singleton
class SpectatorRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    /**
     * Fetches the host's [DayInfo] list for [startDate]..[endDate] from Firestore.
     * Returns an empty list if the host has no anchor configured.
     */
    suspend fun getDayInfosForRange(
        hostUid: String,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<DayInfo> {
        val userDoc = firestore.collection("users").document(hostUid).get().await()
        val anchorDateStr = userDoc.getString("anchorDate") ?: return emptyList()
        val anchorCycleIndex = userDoc.getLong("anchorCycleIndex")?.toInt() ?: return emptyList()
        if (anchorCycleIndex < 0) return emptyList()

        val anchorDate = LocalDate.parse(anchorDateStr)
        val cadenceMap = CadenceEngine.shiftTypesForRange(startDate, endDate, anchorDate, anchorCycleIndex)

        val startStr = startDate.toString()
        val endStr = endDate.toString()
        val userRef = firestore.collection("users").document(hostUid)

        val shiftsSnap = userRef.collection("shifts")
            .whereGreaterThanOrEqualTo("date", startStr)
            .whereLessThanOrEqualTo("date", endStr)
            .get().await()

        val leavesSnap = userRef.collection("leaves")
            .whereGreaterThanOrEqualTo("date", startStr)
            .whereLessThanOrEqualTo("date", endStr)
            .get().await()

        val overtimeSnap = userRef.collection("overtime")
            .whereGreaterThanOrEqualTo("date", startStr)
            .whereLessThanOrEqualTo("date", endStr)
            .get().await()

        val shiftByDate = shiftsSnap.documents.associateBy { it.getString("date") }
        val leaveByDate = leavesSnap.documents.associateBy { it.getString("date") }
        val overtimeDates = overtimeSnap.documents.mapNotNull { it.getString("date") }.toSet()

        val result = mutableListOf<DayInfo>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            val dateStr = current.toString()
            val shiftDoc = shiftByDate[dateStr]
            val leaveDoc = leaveByDate[dateStr]
            val hasOt = dateStr in overtimeDates

            val isManualOverride = shiftDoc?.getBoolean("isManualOverride") == true
            val leaveType = leaveDoc?.getString("leaveType")?.let {
                runCatching { LeaveType.valueOf(it) }.getOrNull()
            }
            val halfDay = leaveDoc?.getBoolean("halfDay") == true

            val dayInfo = when {
                isManualOverride -> {
                    val shiftType = shiftDoc?.getString("shiftType")?.let {
                        runCatching { ShiftType.valueOf(it) }.getOrNull()
                    } ?: cadenceMap[current] ?: ShiftType.OFF
                    DayInfo(
                        date = current,
                        shiftType = shiftType,
                        isManualOverride = true,
                        hasLeave = leaveDoc != null,
                        halfDay = halfDay,
                        leaveType = leaveType,
                        hasOvertime = hasOt,
                        note = shiftDoc?.getString("note"),
                    )
                }
                leaveDoc != null -> DayInfo(
                    date = current,
                    shiftType = if (halfDay) cadenceMap[current] ?: ShiftType.OFF else ShiftType.LEAVE,
                    hasLeave = true,
                    halfDay = halfDay,
                    leaveType = leaveType,
                    hasOvertime = hasOt,
                )
                else -> DayInfo(
                    date = current,
                    shiftType = cadenceMap[current] ?: ShiftType.OFF,
                    hasOvertime = hasOt,
                )
            }
            result.add(dayInfo)
            current = current.plusDays(1)
        }
        return result
    }
}
