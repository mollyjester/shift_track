package com.slikharev.shifttrack.data.repository

import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.auth.requireUserId
import com.slikharev.shifttrack.data.local.db.dao.AttachmentDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks attachment storage usage against the per-user quota.
 *
 * The 500 MB limit is enforced against the local DAO sum, which mirrors
 * what will eventually be uploaded to Firebase Storage.
 */
@Singleton
class StorageMonitor @Inject constructor(
    private val attachmentDao: AttachmentDao,
    private val userSession: UserSession,
) {
    companion object {
        const val MAX_STORAGE_BYTES: Long = 500L * 1024 * 1024       // 500 MB
        const val WARNING_THRESHOLD: Long = MAX_STORAGE_BYTES * 9 / 10  // 450 MB (90%)
    }

    data class StorageStatus(
        val usedBytes: Long,
        val maxBytes: Long = MAX_STORAGE_BYTES,
        val isOverQuota: Boolean = usedBytes >= maxBytes,
        val isNearQuota: Boolean = usedBytes >= WARNING_THRESHOLD,
    )

    suspend fun checkQuota(): StorageStatus {
        val used = attachmentDao.getTotalSizeForUser(userSession.requireUserId())
        return StorageStatus(usedBytes = used)
    }

    suspend fun canAddFile(fileSizeBytes: Long): Boolean {
        val used = attachmentDao.getTotalSizeForUser(userSession.requireUserId())
        return (used + fileSizeBytes) <= MAX_STORAGE_BYTES
    }
}
