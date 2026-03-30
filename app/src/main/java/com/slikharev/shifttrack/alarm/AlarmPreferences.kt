package com.slikharev.shifttrack.alarm

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed accessor for alarm-specific DataStore preferences.
 *
 * Wraps [DataStore] so that all alarm preference reads/writes are in one
 * place — deleting this class (and [AlarmConstants]) removes all alarm prefs.
 */
// [EXPERIMENTAL:ALARM]
@Singleton
class AlarmPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val KEY_ENABLED = booleanPreferencesKey(AlarmConstants.PREF_ALARM_FEATURE_ENABLED)
        private val KEY_TRIGGER_TIME = stringPreferencesKey(AlarmConstants.PREF_ALARM_TRIGGER_TIME)
        private val KEY_ALARM_COUNT = intPreferencesKey(AlarmConstants.PREF_ALARM_COUNT)
        private val KEY_INTERVAL_MINUTES = intPreferencesKey(AlarmConstants.PREF_ALARM_INTERVAL_MINUTES)
        private val KEY_FIRST_TIME = stringPreferencesKey(AlarmConstants.PREF_ALARM_FIRST_TIME)

        /**
         * Computes the list of alarm times based on the given parameters.
         * Example: first="04:30", count=4, interval=10 → ["04:30", "04:40", "04:50", "05:00"]
         */
        fun computeAlarmTimes(firstTime: String, count: Int, intervalMinutes: Int): List<String> {
            val parts = firstTime.split(":")
            val startHour = parts[0].toIntOrNull() ?: 4
            val startMinute = parts.getOrNull(1)?.toIntOrNull() ?: 30
            val totalMinutes = startHour * 60 + startMinute
            return (0 until count).map { i ->
                val m = totalMinutes + i * intervalMinutes
                val h = (m / 60) % 24
                val min = m % 60
                "%02d:%02d".format(h, min)
            }
        }
    }

    // ── Flows ────────────────────────────────────────────────────────────────

    val enabled: Flow<Boolean> = dataStore.data.map { it[KEY_ENABLED] ?: false }
    val triggerTime: Flow<String> = dataStore.data.map { it[KEY_TRIGGER_TIME] ?: AlarmConstants.DEFAULT_TRIGGER_TIME }
    val alarmCount: Flow<Int> = dataStore.data.map { it[KEY_ALARM_COUNT] ?: AlarmConstants.DEFAULT_ALARM_COUNT }
    val intervalMinutes: Flow<Int> = dataStore.data.map { it[KEY_INTERVAL_MINUTES] ?: AlarmConstants.DEFAULT_INTERVAL_MINUTES }
    val firstAlarmTime: Flow<String> = dataStore.data.map { it[KEY_FIRST_TIME] ?: AlarmConstants.DEFAULT_FIRST_ALARM_TIME }

    // ── Setters ──────────────────────────────────────────────────────────────

    suspend fun setEnabled(value: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = value }
    }

    suspend fun setTriggerTime(hhmm: String) {
        dataStore.edit { it[KEY_TRIGGER_TIME] = hhmm }
    }

    suspend fun setAlarmCount(count: Int) {
        dataStore.edit { it[KEY_ALARM_COUNT] = count.coerceIn(1, 10) }
    }

    suspend fun setIntervalMinutes(minutes: Int) {
        dataStore.edit { it[KEY_INTERVAL_MINUTES] = minutes.coerceIn(5, 20) }
    }

    suspend fun setFirstAlarmTime(hhmm: String) {
        dataStore.edit { it[KEY_FIRST_TIME] = hhmm }
    }

    // ── Snapshot reads (for receivers) ───────────────────────────────────────

    suspend fun readEnabled(): Boolean = dataStore.data.first()[KEY_ENABLED] ?: false
    suspend fun readTriggerTime(): String = dataStore.data.first()[KEY_TRIGGER_TIME] ?: AlarmConstants.DEFAULT_TRIGGER_TIME
    suspend fun readAlarmCount(): Int = dataStore.data.first()[KEY_ALARM_COUNT] ?: AlarmConstants.DEFAULT_ALARM_COUNT
    suspend fun readIntervalMinutes(): Int = dataStore.data.first()[KEY_INTERVAL_MINUTES] ?: AlarmConstants.DEFAULT_INTERVAL_MINUTES
    suspend fun readFirstAlarmTime(): String = dataStore.data.first()[KEY_FIRST_TIME] ?: AlarmConstants.DEFAULT_FIRST_ALARM_TIME
}
