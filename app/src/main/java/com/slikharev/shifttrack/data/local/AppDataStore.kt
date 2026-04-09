package com.slikharev.shifttrack.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

object PrefsKeys {
    val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    // Phase 2.3: anchor date (ISO "yyyy-MM-dd") + cycle index (0-4)
    // Cycle: [DAY(0), DAY(1), NIGHT(2), REST(3), OFF(4)]
    val ANCHOR_DATE = stringPreferencesKey("anchor_date")
    val ANCHOR_CYCLE_INDEX = intPreferencesKey("anchor_cycle_index")
    // Phase 2.8: annual reset
    val LAST_RESET_YEAR = intPreferencesKey("last_reset_year")
    // Phase 2.10: FCM registration token (persisted so it survives sign-in)
    val FCM_TOKEN = stringPreferencesKey("fcm_token")
    val DEFAULT_LEAVE_DAYS = floatPreferencesKey("default_leave_days")
    // Configurable shift-type colors (stored as ARGB Long)
    val COLOR_DAY = longPreferencesKey("color_day")
    val COLOR_NIGHT = longPreferencesKey("color_night")
    val COLOR_REST = longPreferencesKey("color_rest")
    val COLOR_OFF = longPreferencesKey("color_off")
    val COLOR_LEAVE = longPreferencesKey("color_leave")
    // Configurable leave-type colors (stored as ARGB Long)
    val COLOR_LEAVE_ANNUAL = longPreferencesKey("color_leave_annual")
    val COLOR_LEAVE_SICK = longPreferencesKey("color_leave_sick")
    val COLOR_LEAVE_PERSONAL = longPreferencesKey("color_leave_personal")
    val COLOR_LEAVE_UNPAID = longPreferencesKey("color_leave_unpaid")
    val COLOR_LEAVE_STUDY = longPreferencesKey("color_leave_study")
    // Widget configuration
    val WIDGET_BG_COLOR = longPreferencesKey("widget_bg_color")
    val WIDGET_TRANSPARENCY = floatPreferencesKey("widget_transparency")
    val WIDGET_DAY_COUNT = intPreferencesKey("widget_day_count")
    // Spectator mode: calendar is view-only, no editing
    val SPECTATOR_MODE = booleanPreferencesKey("spectator_mode")
    // Watched hosts (JSON serialized list)
    val WATCHED_HOSTS = stringPreferencesKey("watched_hosts")
    // Currently selected host UID for spectator calendar view
    val SELECTED_HOST_UID = stringPreferencesKey("selected_host_uid")
    // Cached spectator shift data for widget (avoids Firestore calls in widget receiver)
    val SPECTATOR_WIDGET_CACHE = stringPreferencesKey("spectator_widget_cache")
    // Cached spectator calendar data for offline viewing (broader than widget cache)
    val SPECTATOR_CALENDAR_CACHE = stringPreferencesKey("spectator_calendar_cache")
    // Country & income rates
    val SELECTED_COUNTRY_CODE = stringPreferencesKey("selected_country_code")
    val BASE_HOURLY_RATE = floatPreferencesKey("base_hourly_rate")
    val NIGHT_MULTIPLIER = floatPreferencesKey("night_multiplier")
    val WEEKEND_MULTIPLIER = floatPreferencesKey("weekend_multiplier")
    val HOLIDAY_MULTIPLIER = floatPreferencesKey("holiday_multiplier")
    val SHIFT_CHANGEOVER_HOUR = intPreferencesKey("shift_changeover_hour")
}

/**
 * Typed accessor for the app's DataStore<Preferences> store.
 *
 * Keys stored:
 * - `onboarding_complete` (Boolean) — set once after the user finishes onboarding
 * - `anchor_date` (String, ISO-8601) — the date used as cycle index 0 reference
 * - `anchor_cycle_index` (Int, 0–4) — which cycle position the anchor falls on
 * - `last_reset_year` (Int) — the year in which the annual leave roll-over last ran
 * - `fcm_token` (String?) — the most recently received FCM registration token
 */
@Singleton
class AppDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val onboardingComplete: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.ONBOARDING_COMPLETE] ?: false }

    val anchorDate: Flow<String?> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.ANCHOR_DATE] }

    /** 0 = first DAY, 1 = second DAY, 2 = NIGHT, 3 = REST, 4 = OFF. -1 means not set. */
    val anchorCycleIndex: Flow<Int> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.ANCHOR_CYCLE_INDEX] ?: -1 }

    val lastResetYear: Flow<Int> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.LAST_RESET_YEAR] ?: 0 }

    val pendingFcmToken: Flow<String?> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.FCM_TOKEN] }

    val defaultLeaveDays: Flow<Float> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.DEFAULT_LEAVE_DAYS] ?: DEFAULT_LEAVE_DAYS }

    suspend fun setDefaultLeaveDays(days: Float) {
        dataStore.edit { it[PrefsKeys.DEFAULT_LEAVE_DAYS] = days }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[PrefsKeys.ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setAnchor(date: String, cycleIndex: Int) {
        dataStore.edit {
            it[PrefsKeys.ANCHOR_DATE] = date
            it[PrefsKeys.ANCHOR_CYCLE_INDEX] = cycleIndex
        }
    }

    suspend fun setLastResetYear(year: Int) {
        dataStore.edit { it[PrefsKeys.LAST_RESET_YEAR] = year }
    }

    suspend fun setPendingFcmToken(token: String?) {
        dataStore.edit { prefs ->
            if (token != null) prefs[PrefsKeys.FCM_TOKEN] = token
            else prefs.remove(PrefsKeys.FCM_TOKEN)
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    // ── Shift-type colors ────────────────────────────────────────────────────────

    val colorDay: Flow<Long?> = dataStore.data.map { it[PrefsKeys.COLOR_DAY] }
    val colorNight: Flow<Long?> = dataStore.data.map { it[PrefsKeys.COLOR_NIGHT] }
    val colorRest: Flow<Long?> = dataStore.data.map { it[PrefsKeys.COLOR_REST] }
    val colorOff: Flow<Long?> = dataStore.data.map { it[PrefsKeys.COLOR_OFF] }
    val colorLeave: Flow<Long?> = dataStore.data.map { it[PrefsKeys.COLOR_LEAVE] }

    suspend fun setShiftColor(key: androidx.datastore.preferences.core.Preferences.Key<Long>, argb: Long) {
        dataStore.edit { it[key] = argb }
    }

    // ── Leave-type colors ─────────────────────────────────────────────────────

    val colorLeaveAnnual: Flow<Long?> = dataStore.data.map { it[PrefsKeys.COLOR_LEAVE_ANNUAL] }
    val colorLeaveSick: Flow<Long?> = dataStore.data.map { it[PrefsKeys.COLOR_LEAVE_SICK] }
    val colorLeavePersonal: Flow<Long?> = dataStore.data.map { it[PrefsKeys.COLOR_LEAVE_PERSONAL] }
    val colorLeaveUnpaid: Flow<Long?> = dataStore.data.map { it[PrefsKeys.COLOR_LEAVE_UNPAID] }
    val colorLeaveStudy: Flow<Long?> = dataStore.data.map { it[PrefsKeys.COLOR_LEAVE_STUDY] }

    suspend fun setLeaveColor(key: Preferences.Key<Long>, argb: Long) {
        dataStore.edit { it[key] = argb }
    }

    // ── Widget configuration ─────────────────────────────────────────────────────

    val widgetBgColor: Flow<Long?> = dataStore.data.map { it[PrefsKeys.WIDGET_BG_COLOR] }
    val widgetTransparency: Flow<Float> = dataStore.data.map { it[PrefsKeys.WIDGET_TRANSPARENCY] ?: DEFAULT_WIDGET_TRANSPARENCY }
    val widgetDayCount: Flow<Int> = dataStore.data.map { it[PrefsKeys.WIDGET_DAY_COUNT] ?: DEFAULT_WIDGET_DAY_COUNT }

    suspend fun setWidgetBgColor(argb: Long) {
        dataStore.edit { it[PrefsKeys.WIDGET_BG_COLOR] = argb }
    }

    suspend fun setWidgetTransparency(alpha: Float) {
        dataStore.edit { it[PrefsKeys.WIDGET_TRANSPARENCY] = alpha.coerceIn(0f, 1f) }
    }

    suspend fun setWidgetDayCount(count: Int) {
        dataStore.edit { it[PrefsKeys.WIDGET_DAY_COUNT] = count.coerceIn(MIN_WIDGET_DAYS, MAX_WIDGET_DAYS) }
    }

    // ── Spectator mode ───────────────────────────────────────────────────────────

    val spectatorMode: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.SPECTATOR_MODE] ?: false }

    suspend fun setSpectatorMode(enabled: Boolean) {
        dataStore.edit { it[PrefsKeys.SPECTATOR_MODE] = enabled }
    }

    // ── Watched hosts (spectator feature) ─────────────────────────────────────

    data class WatchedHost(val uid: String, val displayName: String)

    val watchedHosts: Flow<List<WatchedHost>> = dataStore.data
        .map { prefs ->
            val raw = prefs[PrefsKeys.WATCHED_HOSTS] ?: return@map emptyList()
            decodeWatchedHosts(raw)
        }

    suspend fun addWatchedHost(uid: String, displayName: String) {
        dataStore.edit { prefs ->
            val current = prefs[PrefsKeys.WATCHED_HOSTS]?.let { decodeWatchedHosts(it) } ?: emptyList()
            if (current.none { it.uid == uid }) {
                prefs[PrefsKeys.WATCHED_HOSTS] = encodeWatchedHosts(current + WatchedHost(uid, displayName))
            }
        }
    }

    val selectedHostUid: Flow<String?> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.SELECTED_HOST_UID] }

    suspend fun setSelectedHostUid(uid: String?) {
        dataStore.edit { prefs ->
            if (uid != null) prefs[PrefsKeys.SELECTED_HOST_UID] = uid
            else prefs.remove(PrefsKeys.SELECTED_HOST_UID)
        }
    }

    // ── Spectator widget cache ────────────────────────────────────────────────────

    /**
     * Stores pre-fetched spectator shift data so the widget never needs Firestore.
     * Each entry is "date,shiftType,hasLeave,halfDay,leaveType" separated by newlines.
     */
    suspend fun setSpectatorWidgetCache(entries: List<SpectatorWidgetEntry>) {
        val encoded = entries.joinToString("\n") { e ->
            "${e.date},${e.shiftType},${e.hasLeave},${e.halfDay},${e.leaveType ?: ""}"
        }
        dataStore.edit { it[PrefsKeys.SPECTATOR_WIDGET_CACHE] = encoded }
    }

    data class SpectatorWidgetEntry(
        val date: String,
        val shiftType: String,
        val hasLeave: Boolean,
        val halfDay: Boolean,
        val leaveType: String?,
        val isManualOverride: Boolean = false,
        val hasOvertime: Boolean = false,
        val note: String? = null,
    )

    // ── Spectator calendar cache (offline support) ────────────────────────────────

    /**
     * Stores a broader set of spectator shift data for offline calendar viewing.
     * Same encoding as the widget cache. Used by [SpectatorRepository] as a
     * fallback when Firestore is unreachable.
     */
    suspend fun setSpectatorCalendarCache(entries: List<SpectatorWidgetEntry>) {
        val encoded = entries.joinToString("\n") { e ->
            "${e.date},${e.shiftType},${e.hasLeave},${e.halfDay},${e.leaveType ?: ""},${e.isManualOverride},${e.hasOvertime},${e.note?.replace("\n", " ")?.take(200) ?: ""}"
        }
        dataStore.edit { it[PrefsKeys.SPECTATOR_CALENDAR_CACHE] = encoded }
    }

    suspend fun readSpectatorCalendarCache(): List<SpectatorWidgetEntry> {
        val prefs = dataStore.data.first()
        val raw = prefs[PrefsKeys.SPECTATOR_CALENDAR_CACHE] ?: return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split(",")
            if (parts.size >= 5) {
                SpectatorWidgetEntry(
                    date = parts[0],
                    shiftType = parts[1],
                    hasLeave = parts[2].toBooleanStrictOrNull() ?: false,
                    halfDay = parts[3].toBooleanStrictOrNull() ?: false,
                    leaveType = parts[4].ifBlank { null },
                    isManualOverride = parts.getOrNull(5)?.toBooleanStrictOrNull() ?: false,
                    hasOvertime = parts.getOrNull(6)?.toBooleanStrictOrNull() ?: false,
                    note = parts.getOrNull(7)?.ifBlank { null },
                )
            } else null
        }
    }

    // ── Atomic snapshot for widget rendering ─────────────────────────────────────

    /**
     * Reads all widget-related preferences in a single [DataStore] access,
     * guaranteeing a consistent snapshot.
     */
    suspend fun readWidgetSnapshot(): WidgetSnapshot {
        val prefs = dataStore.data.first()
        val cache = prefs[PrefsKeys.SPECTATOR_WIDGET_CACHE]?.let { raw ->
            raw.lines().filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = line.split(',', limit = 5)
                if (parts.size >= 4) {
                    SpectatorWidgetEntry(
                        date = parts[0],
                        shiftType = parts[1],
                        hasLeave = parts[2].toBooleanStrictOrNull() ?: false,
                        halfDay = parts[3].toBooleanStrictOrNull() ?: false,
                        leaveType = parts.getOrNull(4)?.ifBlank { null },
                    )
                } else null
            }
        } ?: emptyList()
        return WidgetSnapshot(
            anchorDate = prefs[PrefsKeys.ANCHOR_DATE],
            anchorCycleIndex = prefs[PrefsKeys.ANCHOR_CYCLE_INDEX] ?: -1,
            bgColor = prefs[PrefsKeys.WIDGET_BG_COLOR],
            transparency = prefs[PrefsKeys.WIDGET_TRANSPARENCY] ?: DEFAULT_WIDGET_TRANSPARENCY,
            dayCount = prefs[PrefsKeys.WIDGET_DAY_COUNT] ?: DEFAULT_WIDGET_DAY_COUNT,
            colorDay = prefs[PrefsKeys.COLOR_DAY],
            colorNight = prefs[PrefsKeys.COLOR_NIGHT],
            colorRest = prefs[PrefsKeys.COLOR_REST],
            colorOff = prefs[PrefsKeys.COLOR_OFF],
            colorLeave = prefs[PrefsKeys.COLOR_LEAVE],
            colorLeaveAnnual = prefs[PrefsKeys.COLOR_LEAVE_ANNUAL],
            colorLeaveSick = prefs[PrefsKeys.COLOR_LEAVE_SICK],
            colorLeavePersonal = prefs[PrefsKeys.COLOR_LEAVE_PERSONAL],
            colorLeaveUnpaid = prefs[PrefsKeys.COLOR_LEAVE_UNPAID],
            colorLeaveStudy = prefs[PrefsKeys.COLOR_LEAVE_STUDY],
            spectatorMode = prefs[PrefsKeys.SPECTATOR_MODE] ?: false,
            selectedHostUid = prefs[PrefsKeys.SELECTED_HOST_UID],
            spectatorCache = cache,
        )
    }

    data class WidgetSnapshot(
        val anchorDate: String?,
        val anchorCycleIndex: Int,
        val bgColor: Long?,
        val transparency: Float,
        val dayCount: Int,
        val colorDay: Long?,
        val colorNight: Long?,
        val colorRest: Long?,
        val colorOff: Long?,
        val colorLeave: Long?,
        val colorLeaveAnnual: Long? = null,
        val colorLeaveSick: Long? = null,
        val colorLeavePersonal: Long? = null,
        val colorLeaveUnpaid: Long? = null,
        val colorLeaveStudy: Long? = null,
        val spectatorMode: Boolean = false,
        val selectedHostUid: String? = null,
        val spectatorCache: List<SpectatorWidgetEntry> = emptyList(),
    )

    // ── Country & income rates ─────────────────────────────────────────────────

    val selectedCountryCode: Flow<String?> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.SELECTED_COUNTRY_CODE] }

    val baseHourlyRate: Flow<Float> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.BASE_HOURLY_RATE] ?: 0f }

    val nightMultiplier: Flow<Float> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.NIGHT_MULTIPLIER] ?: DEFAULT_NIGHT_MULTIPLIER }

    val weekendMultiplier: Flow<Float> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.WEEKEND_MULTIPLIER] ?: DEFAULT_WEEKEND_MULTIPLIER }

    val holidayMultiplier: Flow<Float> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.HOLIDAY_MULTIPLIER] ?: DEFAULT_HOLIDAY_MULTIPLIER }

    val shiftChangeoverHour: Flow<Int> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.SHIFT_CHANGEOVER_HOUR] ?: DEFAULT_CHANGEOVER_HOUR }

    suspend fun setSelectedCountryCode(code: String) {
        dataStore.edit { it[PrefsKeys.SELECTED_COUNTRY_CODE] = code }
    }

    suspend fun setBaseHourlyRate(rate: Float) {
        dataStore.edit { it[PrefsKeys.BASE_HOURLY_RATE] = rate.coerceAtLeast(0f) }
    }

    suspend fun setNightMultiplier(multiplier: Float) {
        dataStore.edit { it[PrefsKeys.NIGHT_MULTIPLIER] = multiplier.coerceAtLeast(1f) }
    }

    suspend fun setWeekendMultiplier(multiplier: Float) {
        dataStore.edit { it[PrefsKeys.WEEKEND_MULTIPLIER] = multiplier.coerceAtLeast(1f) }
    }

    suspend fun setHolidayMultiplier(multiplier: Float) {
        dataStore.edit { it[PrefsKeys.HOLIDAY_MULTIPLIER] = multiplier.coerceAtLeast(1f) }
    }

    suspend fun setShiftChangeoverHour(hour: Int) {
        dataStore.edit { it[PrefsKeys.SHIFT_CHANGEOVER_HOUR] = hour.coerceIn(0, 23) }
    }

    companion object {
        const val DEFAULT_LEAVE_DAYS = 28f
        const val DEFAULT_WIDGET_TRANSPARENCY = 1f
        const val DEFAULT_WIDGET_DAY_COUNT = 4
        const val MIN_WIDGET_DAYS = 1
        const val MAX_WIDGET_DAYS = 7
        const val DEFAULT_NIGHT_MULTIPLIER = 1f
        const val DEFAULT_WEEKEND_MULTIPLIER = 1f
        const val DEFAULT_HOLIDAY_MULTIPLIER = 1f
        const val DEFAULT_CHANGEOVER_HOUR = 7

        /** Encode as "uid1|name1\nuid2|name2" */
        internal fun encodeWatchedHosts(hosts: List<WatchedHost>): String =
            hosts.joinToString("\n") { "${it.uid}|${it.displayName}" }

        internal fun decodeWatchedHosts(raw: String): List<WatchedHost> =
            raw.lines()
                .filter { it.contains('|') }
                .map { line ->
                    val parts = line.split('|', limit = 2)
                    WatchedHost(uid = parts[0], displayName = parts.getOrElse(1) { "" })
                }
    }
}
