package com.slikharev.shifttrack.data.repository

import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.auth.requireUserId
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.dao.LeaveDao
import com.slikharev.shifttrack.data.local.db.dao.OvertimeDao
import com.slikharev.shifttrack.data.local.db.dao.ShiftDao
import com.slikharev.shifttrack.data.local.db.entity.OvertimeEntity
import com.slikharev.shifttrack.data.local.db.entity.ShiftEntity
import com.slikharev.shifttrack.engine.CadenceEngine
import com.slikharev.shifttrack.model.DayInfo
import com.slikharev.shifttrack.model.LeaveType
import com.slikharev.shifttrack.model.ShiftType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftRepository @Inject constructor(
    private val shiftDao: ShiftDao,
    private val leaveDao: LeaveDao,
    private val overtimeDao: OvertimeDao,
    private val appDataStore: AppDataStore,
    private val userSession: UserSession,
) {
    /**
     * Returns a [Flow] of [DayInfo] list for every day in [startDate]..[endDate] inclusive.
     *
     * Strategy (in priority order for each day):
     *  1. If a [ShiftEntity] with `isManualOverride = true` exists → use it.
     *  2. If a leave record exists for the day → shiftType = LEAVE.
     *  3. Otherwise → compute from [CadenceEngine] (requires anchor to be set).
     *
     * Emits an empty list if the anchor is not configured yet (anchorCycleIndex == -1).
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun getDayInfosForRange(startDate: LocalDate, endDate: LocalDate): Flow<List<DayInfo>> {
        require(!startDate.isAfter(endDate)) { "startDate must not be after endDate" }
        val startStr = startDate.toString()
        val endStr = endDate.toString()

        return combine(
            appDataStore.anchorDate,
            appDataStore.anchorCycleIndex,
        ) { anchorDateStr, cycleIndex -> anchorDateStr to cycleIndex }
            .debounce(100)
            .flatMapLatest { (anchorDateStr, cycleIndex) ->
                if (anchorDateStr == null || cycleIndex < 0) {
                    return@flatMapLatest flowOf(emptyList())
                }
                val anchorDate = LocalDate.parse(anchorDateStr)
                val cadenceMap = CadenceEngine.shiftTypesForRange(
                    startDate = startDate,
                    endDate = endDate,
                    anchorDate = anchorDate,
                    anchorCycleIndex = cycleIndex,
                )

                val uid = userSession.requireUserId()
                combine(
                    shiftDao.getShiftsForRange(
                        userId = uid,
                        startDate = startStr,
                        endDate = endStr,
                    ),
                    leaveDao.getLeavesForRange(
                        userId = uid,
                        startDate = startStr,
                        endDate = endStr,
                    ),
                    overtimeDao.getOvertimeForRange(
                        userId = uid,
                        startDate = startStr,
                        endDate = endStr,
                    ),
                ) { shifts, leaves, overtimes ->
                    val shiftByDate = shifts.associateBy { it.date }
                    val leaveByDate = leaves.associateBy { it.date }
                    val overtimeDates = overtimes.map { it.date }.toSet()

                    var current = startDate
                    val result = mutableListOf<DayInfo>()
                    while (!current.isAfter(endDate)) {
                        val dateStr = current.toString()
                        val entity = shiftByDate[dateStr]
                        val leave = leaveByDate[dateStr]
                        val hasOt = dateStr in overtimeDates
                        val leaveType = leave?.let {
                            LeaveType.fromString(it.leaveType)
                        }
                        val dayInfo = when {
                            entity != null && entity.isManualOverride ->
                                DayInfo(
                                    date = current,
                                    shiftType = ShiftType.valueOf(entity.shiftType),
                                    isManualOverride = true,
                                    hasLeave = leave != null,
                                    halfDay = leave?.halfDay == true,
                                    leaveType = leaveType,
                                    hasOvertime = hasOt,
                                    note = entity.note,
                                )
                            leave != null ->
                                DayInfo(
                                    date = current,
                                    shiftType = if (leave.halfDay) {
                                        cadenceMap[current] ?: ShiftType.OFF
                                    } else {
                                        ShiftType.LEAVE
                                    },
                                    hasLeave = true,
                                    halfDay = leave.halfDay,
                                    leaveType = leaveType,
                                    hasOvertime = hasOt,
                                )
                            else ->
                                DayInfo(
                                    date = current,
                                    shiftType = cadenceMap[current] ?: ShiftType.OFF,
                                    hasOvertime = hasOt,
                                )
                        }
                        result.add(dayInfo)
                        current = current.plusDays(1)
                    }
                    result
                }
            }
    }

    /**
     * Writes a manual shift override for the given day.
     * Also writes a corresponding DB row so the sync worker can push it to Firestore.
     */
    suspend fun setManualOverride(
        userId: String,
        date: LocalDate,
        shiftType: ShiftType,
        note: String? = null,
    ) {
        shiftDao.upsert(
            ShiftEntity(
                date = date.toString(),
                shiftType = shiftType.name,
                isManualOverride = true,
                note = note,
                userId = userId,
                synced = false,
            ),
        )
    }

    /** Removes a manual override, reverting the day to cadence-computed. */
    suspend fun clearManualOverride(userId: String, date: LocalDate) {
        shiftDao.deleteByDate(userId, date.toString())
    }

    /** Returns overtime entries for the given date range (one-shot). */
    suspend fun getOvertimeForRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<OvertimeEntity> {
        val uid = userSession.requireUserId()
        return overtimeDao.getOvertimeForRange(uid, startDate.toString(), endDate.toString()).first()
    }

    companion object {
        // userId is now sourced from UserSession — this stub is kept for unit tests only
        internal const val CURRENT_USER_PLACEHOLDER = ""
    }
}
