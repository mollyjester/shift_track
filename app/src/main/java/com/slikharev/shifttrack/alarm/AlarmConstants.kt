package com.slikharev.shifttrack.alarm

/**
 * Constants for the experimental auto wake-up alarm feature.
 *
 * Centralises preference keys, default values, notification identifiers,
 * and intent extras so they are easy to find when removing the feature.
 */
// [EXPERIMENTAL:ALARM]
object AlarmConstants {

    // ── DataStore preference keys ────────────────────────────────────────────

    const val PREF_ALARM_FEATURE_ENABLED = "alarm_feature_enabled"
    const val PREF_ALARM_TRIGGER_TIME = "alarm_trigger_time"
    const val PREF_ALARM_COUNT = "alarm_count"
    const val PREF_ALARM_INTERVAL_MINUTES = "alarm_interval_minutes"
    const val PREF_ALARM_FIRST_TIME = "alarm_first_time"

    // ── Default values ───────────────────────────────────────────────────────

    const val DEFAULT_TRIGGER_TIME = "21:00"
    const val DEFAULT_ALARM_COUNT = 4
    const val DEFAULT_INTERVAL_MINUTES = 10
    const val DEFAULT_FIRST_ALARM_TIME = "04:30"

    // ── Notification ─────────────────────────────────────────────────────────

    const val NOTIFICATION_CHANNEL_ID = "alarm_trigger"
    const val NOTIFICATION_ID = 7001

    // ── Intent extras ────────────────────────────────────────────────────────

    const val EXTRA_TARGET_DATE = "target_date"

    // ── AlarmManager request codes ───────────────────────────────────────────

    const val TRIGGER_REQUEST_CODE = 9002
}
