package com.slikharev.shifttrack.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
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
}

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
}
