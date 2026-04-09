package com.slikharev.shifttrack.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.slikharev.shifttrack.data.local.db.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    /** Reactive stream of attachments for a specific day — drives the DayDetail UI. */
    @Query(
        """SELECT * FROM attachments
           WHERE user_id = :userId AND date = :date
           ORDER BY created_at ASC""",
    )
    fun getAttachmentsForDate(userId: String, date: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
    suspend fun getAttachmentById(id: Long): AttachmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(attachment: AttachmentEntity): Long

    @Delete
    suspend fun delete(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Rows not yet uploaded to Firebase Storage. */
    @Query("SELECT * FROM attachments WHERE user_id = :userId AND synced = 0")
    suspend fun getUnsynced(userId: String): List<AttachmentEntity>

    @Query("UPDATE attachments SET synced = 1, firebase_path = :firebasePath WHERE id = :id")
    suspend fun markSynced(id: Long, firebasePath: String)

    /** Total bytes of all attachments for this user — used for quota enforcement. */
    @Query("SELECT COALESCE(SUM(file_size_bytes), 0) FROM attachments WHERE user_id = :userId")
    suspend fun getTotalSizeForUser(userId: String): Long

    /** Reactive version of total size — drives the Settings storage bar. */
    @Query("SELECT COALESCE(SUM(file_size_bytes), 0) FROM attachments WHERE user_id = :userId")
    fun observeTotalSizeForUser(userId: String): Flow<Long>

    /** Attachments older than a cutoff date — used by the cleanup job. */
    @Query("SELECT * FROM attachments WHERE user_id = :userId AND date < :cutoffDate")
    suspend fun getAttachmentsOlderThan(userId: String, cutoffDate: String): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("SELECT COUNT(*) FROM attachments WHERE user_id = :userId AND date = :date")
    suspend fun getAttachmentCountForDate(userId: String, date: String): Int
}
