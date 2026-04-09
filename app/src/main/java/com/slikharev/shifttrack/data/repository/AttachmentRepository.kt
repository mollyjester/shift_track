package com.slikharev.shifttrack.data.repository

import android.net.Uri
import com.slikharev.shifttrack.auth.UserSession
import com.slikharev.shifttrack.auth.requireUserId
import com.slikharev.shifttrack.data.local.AttachmentFileManager
import com.slikharev.shifttrack.data.local.PhotoCompressor
import com.slikharev.shifttrack.data.local.db.dao.AttachmentDao
import com.slikharev.shifttrack.data.local.db.entity.AttachmentEntity
import com.slikharev.shifttrack.data.remote.FirebaseStorageSyncDataSource
import com.slikharev.shifttrack.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentRepository @Inject constructor(
    private val attachmentDao: AttachmentDao,
    private val fileManager: AttachmentFileManager,
    private val photoCompressor: PhotoCompressor,
    private val userSession: UserSession,
    private val syncScheduler: SyncScheduler,
    private val storageSyncDataSource: FirebaseStorageSyncDataSource,
) {
    private val uid get() = userSession.requireUserId()

    /**
     * Adds an attachment to the given date. Image files exceeding 3 MB are
     * automatically compressed. The file is saved to internal storage and a Room
     * record is created with synced=false.
     */
    suspend fun addAttachment(date: String, uri: Uri, mimeType: String, displayName: String) {
        val isImage = mimeType.startsWith("image/")

        val fileInfo = if (isImage) {
            val compressed = photoCompressor.compressIfNeeded(uri)
            fileManager.saveCompressedPhoto(uid, date, compressed, displayName)
        } else {
            fileManager.saveFile(uid, date, uri, mimeType, displayName)
        }

        val entity = AttachmentEntity(
            date = date,
            userId = uid,
            fileName = fileInfo.originalFileName,
            mimeType = if (isImage) "image/jpeg" else mimeType,
            fileSizeBytes = fileInfo.sizeBytes,
            localPath = fileInfo.localRelativePath,
        )
        attachmentDao.upsert(entity)
        syncScheduler.scheduleImmediateSync()
    }

    /** Reactive stream of attachments for a specific day. */
    fun getAttachmentsForDate(date: String): Flow<List<AttachmentEntity>> =
        attachmentDao.getAttachmentsForDate(uid, date)

    /** Reactive total storage used by this user (bytes). */
    fun observeStorageUsed(): Flow<Long> =
        attachmentDao.observeTotalSizeForUser(uid)

    /** One-shot total storage used. */
    suspend fun getStorageUsed(): Long =
        attachmentDao.getTotalSizeForUser(uid)

    /** Deletes an attachment (local file + Room record). */
    suspend fun deleteAttachment(attachment: AttachmentEntity) {
        fileManager.deleteFile(attachment.localPath)
        attachmentDao.delete(attachment)
        // Firebase Storage cleanup is fire-and-forget
        if (attachment.firebasePath != null) {
            runCatching { storageSyncDataSource.deleteFile(attachment.firebasePath) }
        }
    }

    /**
     * Deletes all attachments older than [months] months.
     * Removes both local files and Room records.
     */
    suspend fun cleanupOlderThan(months: Int) {
        val cutoff = LocalDate.now().minusMonths(months.toLong()).toString()
        val old = attachmentDao.getAttachmentsOlderThan(uid, cutoff)
        if (old.isEmpty()) return

        fileManager.deleteFiles(old.map { it.localPath })
        attachmentDao.deleteByIds(old.map { it.id })

        // Best-effort cloud cleanup
        old.filter { it.firebasePath != null }.forEach { att ->
            runCatching { storageSyncDataSource.deleteFile(att.firebasePath!!) }
        }
    }

    /** Deletes all attachments for the given user (account deletion). */
    suspend fun deleteAllForUser(userId: String) {
        fileManager.deleteFilesForUser(userId)
        attachmentDao.deleteAllForUser(userId)
    }
}
