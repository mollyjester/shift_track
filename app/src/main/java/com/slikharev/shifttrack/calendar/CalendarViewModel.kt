package com.slikharev.shifttrack.calendar

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slikharev.shifttrack.data.local.AppDataStore
import com.slikharev.shifttrack.data.repository.ShiftRepository
import com.slikharev.shifttrack.data.repository.SpectatorRepository
import com.slikharev.shifttrack.widget.ShiftWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val spectatorRepository: SpectatorRepository,
    private val appDataStore: AppDataStore,
    private val widgetUpdater: ShiftWidgetUpdater,
) : ViewModel() {

    private val _currentYearMonth = MutableStateFlow(YearMonth.now())
    val currentYearMonth: StateFlow<YearMonth> = _currentYearMonth.asStateFlow()

    /** True when the user is in spectator-only mode (no own schedule). */
    val isSpectatorOnly: StateFlow<Boolean> = appDataStore.spectatorMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** List of hosts the spectator can view. */
    val watchedHosts: StateFlow<List<AppDataStore.WatchedHost>> = appDataStore.watchedHosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** UID of the currently selected host, or null for "My" calendar. */
    private val _selectedHostUid = MutableStateFlow<String?>(null)
    val selectedHostUid: StateFlow<String?> = _selectedHostUid.asStateFlow()

    /** Non-null when the spectator fetch failed or returned no data. */
    private val _spectatorError = MutableStateFlow<String?>(null)
    val spectatorError: StateFlow<String?> = _spectatorError.asStateFlow()

    /** Incremented to force a re-fetch of spectator data. */
    private val _refreshTrigger = MutableStateFlow(0)

    /** True while a pull-to-refresh is in progress. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        // Restore last selected host from DataStore
        viewModelScope.launch {
            appDataStore.selectedHostUid.collect { uid ->
                _selectedHostUid.value = uid
            }
        }
    }

    fun selectHost(uid: String?) {
        _selectedHostUid.value = uid
        viewModelScope.launch { appDataStore.setSelectedHostUid(uid) }
    }

    /**
     * Flat list of [CalendarDay] cells (always a multiple of 7) for the current month.
     * Switches between local ShiftRepository and remote SpectatorRepository
     * based on the selected host.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val calendarDays: StateFlow<List<CalendarDay>> = combine(
        _currentYearMonth,
        _selectedHostUid,
        _refreshTrigger,
    ) { ym, hostUid, _ -> ym to hostUid }
        .flatMapLatest { (ym, hostUid) ->
            val start = ym.atDay(1)
            val end = ym.atEndOfMonth()
            if (hostUid == null) {
                // Own calendar
                _spectatorError.value = null
                shiftRepository.getDayInfosForRange(start, end).map { dayInfos ->
                    buildCalendarDays(ym, dayInfos)
                }
            } else {
                // Spectated host's calendar (one-shot fetch wrapped in a flow)
                flow {
                    val dayInfos = try {
                        spectatorRepository.getDayInfosForRange(hostUid, start, end)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    _spectatorError.value = if (dayInfos.isEmpty()) {
                        "Could not load schedule — the host may need to open their app to sync"
                    } else {
                        null
                    }
                    // Update widget cache when this month contains today
                    if (dayInfos.isNotEmpty() && ym == YearMonth.now()) {
                        val today = java.time.LocalDate.now()
                        val futureDays = dayInfos.filter { !it.date.isBefore(today) }
                        val maxDays = AppDataStore.MAX_WIDGET_DAYS

                        // If the current month doesn't have enough days left,
                        // fetch the next month's days to fill the widget cache.
                        val cacheInfos = if (futureDays.size < maxDays) {
                            val nextStart = end.plusDays(1)
                            val nextEnd = today.plusDays(maxDays.toLong() - 1)
                            val extraDays = try {
                                spectatorRepository.getDayInfosForRange(hostUid, nextStart, nextEnd)
                            } catch (_: Exception) {
                                emptyList()
                            }
                            (futureDays + extraDays).take(maxDays)
                        } else {
                            futureDays.take(maxDays)
                        }
                        if (cacheInfos.isNotEmpty()) {
                            val entries = cacheInfos.map { d ->
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
                    }
                    emit(buildCalendarDays(ym, dayInfos))
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = buildCalendarDays(_currentYearMonth.value, emptyList()),
        )

    fun prevMonth() = _currentYearMonth.update { it.minusMonths(1) }
    fun nextMonth() = _currentYearMonth.update { it.plusMonths(1) }
    fun setMonth(month: YearMonth) { _currentYearMonth.value = month }

    /** Jump back to the current month. */
    fun goToToday() {
        _currentYearMonth.value = YearMonth.now()
    }

    /** Force a re-fetch of spectator data for the current month. */
    fun refreshSpectator() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshTrigger.update { it + 1 }
            // Small delay so the combine triggers a new flatMapLatest collect
            kotlinx.coroutines.delay(300)
            _isRefreshing.value = false
        }
    }

    // ── CSV Export ────────────────────────────────────────────────────────────────

    private val _exportUri = MutableStateFlow<Uri?>(null)
    val exportUri: StateFlow<Uri?> = _exportUri.asStateFlow()

    fun clearExportUri() {
        _exportUri.value = null
    }

    fun exportCsv(context: Context, startDate: LocalDate, endDate: LocalDate) {
        viewModelScope.launch {
            val dayInfos = shiftRepository.getDayInfosForRange(startDate, endDate).first()
            val overtimes = shiftRepository.getOvertimeForRange(startDate, endDate)
            val overtimeByDate = overtimes.associate { it.date to it.hours }

            val rows = dayInfos.map { info ->
                ExportRow(
                    date = info.date,
                    shiftType = info.shiftType,
                    leaveType = info.leaveType,
                    halfDay = info.halfDay,
                    overtimeHours = overtimeByDate[info.date.toString()] ?: 0f,
                    note = info.note,
                )
            }

            val csv = CsvExporter.generateCsv(rows)
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, "shifts_${startDate}_$endDate.csv")
            file.writeText(csv)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            _exportUri.value = uri
        }
    }
}
