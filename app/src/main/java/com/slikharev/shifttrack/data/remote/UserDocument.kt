package com.slikharev.shifttrack.data.remote

data class UserDocument(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val spectators: List<String> = emptyList(),
)
