package com.slikharev.shifttrack.data.repository

import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.dao.LeaveDao
import com.slikharev.shifttrack.data.local.db.dao.ShiftDao
import com.slikharev.shifttrack.data.local.db.entity.ShiftEntity
import com.slikharev.shifttrack.engine.CadenceEngine
import com.slikharev.shifttrack.model.DayInfo
import com.slikharev.shifttrack.model.ShiftType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftRepository @Inject constructor(
    private val shiftDao: ShiftDao,
    private val leaveDao: LeaveDao,
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
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDayInfosForRange(startDate: LocalDate, endDate: LocalDate): Flow<List<DayInfo>> {
        val startStr = startDate.toString()
        val endStr = endDate.toString()

        return combine(
            appDataStore.anchorDate,
            appDataStore.anchorCycleIndex,
        ) { anchorDateStr, cycleIndex -> anchorDateStr to cycleIndex }
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

                combine(
                    shiftDao.getShiftsForRange(
                        userId = userSession.currentUserId.orEmpty(),
                        startDate = startStr,
                        endDate = endStr,
                    ),
                    leaveDao.getLeavesForRange(
                        userId = userSession.currentUserId.orEmpty(),
                        startDate = startStr,
                        endDate = endStr,
                    ),
                ) { shifts, leaves ->
                    val shiftByDate = shifts.associateBy { it.date }
                    val leaveDates = leaves.map { it.date }.toSet()

                    var current = startDate
                    val result = mutableListOf<DayInfo>()
                    while (!current.isAfter(endDate)) {
                        val dateStr = current.toString()
                        val entity = shiftByDate[dateStr]
                        val dayInfo = when {
                            entity != null && entity.isManualOverride ->
                                DayInfo(
                                    date = current,
                                    shiftType = ShiftType.valueOf(entity.shiftType),
                                    isManualOverride = true,
                                    hasLeave = dateStr in leaveDates,
                                    hasOvertime = false,
                                    note = entity.note,
                                )
                            dateStr in leaveDates ->
                                DayInfo(
                                    date = current,
                                    shiftType = ShiftType.LEAVE,
                                    hasLeave = true,
                                )
                            else ->
                                DayInfo(
                                    date = current,
                                    shiftType = cadenceMap[current] ?: ShiftType.OFF,
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

    companion object {
        // userId is now sourced from UserSession — this stub is kept for unit tests only
        internal const val CURRENT_USER_PLACEHOLDER = ""
    }
}
