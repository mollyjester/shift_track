package com.slikharev.shifttrack.widget

import android.content.Context

/**
 * Lightweight widget error log backed by SharedPreferences.
 * No Hilt required — can be called from any context, including BroadcastReceivers.
 *
 * Keeps the last [MAX_ENTRIES] timestamped entries. Entries are plain strings
 * so they can be shared via Android's standard sharing mechanism.
 */
object WidgetDiagnostics {

    private const val PREFS_NAME = "widget_diagnostics"
    private const val KEY_ERRORS = "errors"
    private const val MAX_ENTRIES = 30

    fun logError(context: Context, source: String, throwable: Throwable) {
        val msg = "${throwable.javaClass.simpleName}: ${throwable.message?.take(200) ?: "(no message)"}"
        val cause = throwable.cause?.let { " caused by ${it.javaClass.simpleName}: ${it.message?.take(100)}" } ?: ""
        append(context, source, msg + cause)
    }

    fun logError(context: Context, source: String, message: String) {
        append(context, source, message)
    }

    /** Returns entries newest-first. */
    fun getErrors(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ERRORS, "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }.reversed()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_ERRORS).apply()
    }

    private fun append(context: Context, source: String, message: String) {
        val ts = java.time.LocalDateTime.now().toString().take(19).replace('T', ' ')
        val entry = "$ts [$source] $message"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = (prefs.getString(KEY_ERRORS, "") ?: "")
            .split("\n").filter { it.isNotBlank() }
        val updated = (existing + entry).takeLast(MAX_ENTRIES).joinToString("\n")
        prefs.edit().putString(KEY_ERRORS, updated).apply()
    }
}
