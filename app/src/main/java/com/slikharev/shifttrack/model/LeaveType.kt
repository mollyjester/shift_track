package com.slikharev.shifttrack.model

enum class LeaveType {
    ANNUAL,
    SICK,
    PERSONAL,
    UNPAID,
    STUDY,
    ;

    companion object {
        /** Parses a [LeaveType] from its [name], treating legacy `"OTHER"` as [STUDY]. */
        fun fromString(value: String): LeaveType? = when (value) {
            "OTHER" -> STUDY
            else -> runCatching { valueOf(value) }.getOrNull()
        }
    }
}
