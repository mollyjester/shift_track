package com.slikharev.shifttrack.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.auth.requireUserId
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.local.db.dao.PublicHolidayDao
import com.slikharev.shifttrack.data.local.db.dao.WorkedHoursOverrideDao
import com.slikharev.shifttrack.data.local.db.entity.LeaveBalanceEntity
import com.slikharev.shifttrack.data.local.db.entity.OvertimeBalanceEntity
import com.slikharev.shifttrack.data.repository.LeaveRepository
import com.slikharev.shifttrack.data.repository.OvertimeRepository
import com.slikharev.shifttrack.data.repository.ShiftRepository
import com.slikharev.shifttrack.data.repository.SpectatorRepository
import com.slikharev.shifttrack.engine.IncomeCalculator
import com.slikharev.shifttrack.engine.IncomeInput
import com.slikharev.shifttrack.model.Countries
import com.slikharev.shifttrack.model.DayInfo
import com.slikharev.shifttrack.widget.ShiftWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class UpcomingDay(
    val dayInfo: DayInfo,
    val isToday: Boolean,
)

private data class RateSettings(
    val baseRate: Float,
    val nightMultiplier: Float,
    val weekendMultiplier: Float,
    val holidayMultiplier: Float,
    val changeoverHour: Int,
)

private data class DataSnapshot(
    val dayInfos: List<DayInfo>,
    val overtimeList: List<com.slikharev.shifttrack.data.local.db.entity.OvertimeEntity>,
    val holidays: List<com.slikharev.shifttrack.data.local.db.entity.PublicHolidayEntity>,
    val overrides: List<com.slikharev.shifttrack.data.local.db.entity.WorkedHoursOverrideEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    leaveRepository: LeaveRepository,
    private val overtimeRepository: OvertimeRepository,
    private val appDataStore: AppDataStore,
    private val spectatorRepository: SpectatorRepository,
    private val widgetUpdater: ShiftWidgetUpdater,
    private val publicHolidayDao: PublicHolidayDao,
    private val workedHoursOverrideDao: WorkedHoursOverrideDao,
    private val userSession: UserSession,
) : ViewModel() {

    private val uid get() = userSession.requireUserId()

    private val today: LocalDate = LocalDate.now()
    private val upcomingEnd: LocalDate = today.plusDays(6)

    /** True when the user is in spectator-only mode (no own schedule). */
    val isSpectatorOnly: StateFlow<Boolean> = appDataStore.spectatorMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The display name of the currently selected host, or null. */
    val selectedHostName: StateFlow<String?> = combine(
        appDataStore.selectedHostUid,
        appDataStore.watchedHosts,
    ) { uid, hosts ->
        uid?.let { id -> hosts.firstOrNull { it.uid == id }?.displayName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Upcoming days. In spectator mode with a selected host, fetched from Firestore.
     * Otherwise fetched from local ShiftRepository.
     */
    val upcomingDays: StateFlow<List<UpcomingDay>> = combine(
        appDataStore.spectatorMode,
        appDataStore.selectedHostUid,
    ) { spectator, hostUid -> spectator to hostUid }
        .flatMapLatest { (isSpectator, hostUid) ->
            if (isSpectator && hostUid != null) {
                // Spectator mode: one-shot fetch from Firestore
                flow {
                    val infos = try {
                        spectatorRepository.getDayInfosForRange(hostUid, today, upcomingEnd)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    // Cache for widget (runs locally, no network)
                    if (infos.isNotEmpty()) {
                        val entries = infos.map { d ->
                            AppDataStore.SpectatorWidgetEntry(
                                date = d.date.toString(),
                                shiftType = d.shiftType.name,
                                hasLeave = d.hasLeave,
                                halfDay = d.halfDay,
                                leaveType = d.leaveType?.name,
                            )
                        }
                        appDataStore.setSpectatorWidgetCache(entries)
                        widgetUpdater.updateAll()
                    }
                    emit(infos.map { UpcomingDay(dayInfo = it, isToday = it.date == today) })
                }
            } else {
                // Normal mode: reactive Flow from local Room
                shiftRepository.getDayInfosForRange(today, upcomingEnd)
                    .map { infos ->
                        infos.map { UpcomingDay(dayInfo = it, isToday = it.date == today) }
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Per-category leave balances for the current calendar year. */
    val leaveBalances: StateFlow<List<LeaveBalanceEntity>> = leaveRepository
        .observeAllBalancesForYear(today.year)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weeklyOvertimeHours: StateFlow<Float> = overtimeRepository
        .getOvertimeForRange(today, upcomingEnd)
        .map { list -> list.sumOf { it.hours.toDouble() }.toFloat() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    val yearlyOvertimeBalance: StateFlow<OvertimeBalanceEntity?> = overtimeRepository
        .observeBalanceForYear(today.year)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ─── Income ─────────────────────────────────────────────────────────────────────

    private val _selectedIncomeMonth = MutableStateFlow(YearMonth.now())
    val selectedIncomeMonth: StateFlow<YearMonth> = _selectedIncomeMonth

    val selectedMonthName: StateFlow<String> = _selectedIncomeMonth
        .map { ym ->
            ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val currencySymbol: StateFlow<String> = appDataStore.selectedCountryCode
        .map { code -> code?.let { Countries.findByCode(it)?.currencySymbol } ?: "$" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "$")

    private val rateSettings = combine(
        appDataStore.baseHourlyRate,
        appDataStore.nightMultiplier,
        appDataStore.weekendMultiplier,
        appDataStore.holidayMultiplier,
        appDataStore.shiftChangeoverHour,
    ) { base, night, weekend, holiday, changeover ->
        RateSettings(base, night, weekend, holiday, changeover)
    }

    private val countryWeekendDay: StateFlow<DayOfWeek> = appDataStore.selectedCountryCode
        .map { code ->
            val country = code?.let { Countries.findByCode(it) }
            Countries.lastWeekendDay(country?.weekendDays ?: setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayOfWeek.SUNDAY)

    private fun incomeForMonth(month: YearMonth): StateFlow<Float> {
        // Query one extra day before+after for night shift boundary hours
        val queryStart = month.atDay(1).minusDays(1)
        val queryEnd = month.atEndOfMonth().plusDays(1)
        val startStr = queryStart.toString()
        val endStr = queryEnd.toString()

        val dataFlows = combine(
            shiftRepository.getDayInfosForRange(queryStart, queryEnd),
            overtimeRepository.getOvertimeForRange(queryStart, queryEnd),
            publicHolidayDao.getHolidaysForRange(uid, startStr, endStr),
            workedHoursOverrideDao.getForRange(uid, startStr, endStr),
        ) { dayInfos, overtimeList, holidays, overrides ->
            DataSnapshot(dayInfos, overtimeList, holidays, overrides)
        }

        return combine(
            dataFlows,
            rateSettings,
            countryWeekendDay,
        ) { data, rates, lastWkDay ->
            val input = IncomeInput(
                dayInfos = data.dayInfos,
                overtimeHours = data.overtimeList.associate { LocalDate.parse(it.date) to it.hours },
                workedHoursOverrides = data.overrides.associate { LocalDate.parse(it.date) to it.hours },
                publicHolidayDates = data.holidays.map { LocalDate.parse(it.date) }.toSet(),
                lastWeekendDay = lastWkDay,
                baseRate = rates.baseRate,
                nightMultiplier = rates.nightMultiplier,
                weekendMultiplier = rates.weekendMultiplier,
                holidayMultiplier = rates.holidayMultiplier,
                shiftChangeoverHour = rates.changeoverHour,
            )
            IncomeCalculator.calculateMonthlyIncome(input, month)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)
    }

    val currentMonthIncome: StateFlow<Float> = _selectedIncomeMonth
        .flatMapLatest { month -> incomeForMonth(month) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    fun navigateMonthBack() {
        _selectedIncomeMonth.value = _selectedIncomeMonth.value.minusMonths(1)
    }

    fun navigateToCurrentMonth() {
        _selectedIncomeMonth.value = YearMonth.now()
    }
}
