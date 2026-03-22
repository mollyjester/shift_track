package com.slikharev.shifttrack.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

object PrefsKeys {
    val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    // Phase 2.8: annual reset
    val LAST_RESET_YEAR = androidx.datastore.preferences.core.intPreferencesKey("last_reset_year")
    // Phase 2.3: anchor date stored as ISO string "yyyy-MM-dd"
    val ANCHOR_DATE = androidx.datastore.preferences.core.stringPreferencesKey("anchor_date")
    val ANCHOR_SHIFT_TYPE = androidx.datastore.preferences.core.stringPreferencesKey("anchor_shift_type")
}

@Singleton
class AppDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val onboardingComplete: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.ONBOARDING_COMPLETE] ?: false }

    val anchorDate: Flow<String?> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.ANCHOR_DATE] }

    val anchorShiftType: Flow<String?> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.ANCHOR_SHIFT_TYPE] }

    val lastResetYear: Flow<Int> = dataStore.data
        .map { prefs -> prefs[PrefsKeys.LAST_RESET_YEAR] ?: 0 }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[PrefsKeys.ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setAnchor(date: String, shiftType: String) {
        dataStore.edit {
            it[PrefsKeys.ANCHOR_DATE] = date
            it[PrefsKeys.ANCHOR_SHIFT_TYPE] = shiftType
        }
    }

    suspend fun setLastResetYear(year: Int) {
        dataStore.edit { it[PrefsKeys.LAST_RESET_YEAR] = year }
    }
}
