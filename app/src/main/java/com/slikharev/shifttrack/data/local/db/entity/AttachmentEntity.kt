package com.slikharev.shifttrack.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per file attachment on a calendar day.
 *
 * Multiple attachments per day are allowed (camera photos, gallery images, documents).
 * The unique index on (date, userId, fileName) prevents duplicate files on the same day.
 * [synced] = false means this file has not yet been uploaded to Firebase Storage.
 * [firebasePath] is populated after a successful upload by [SyncWorker].
 */
@Entity(
    tableName = "attachments",
    indices = [Index(value = ["date", "user_id", "file_name"], unique = true)],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long,
    @ColumnInfo(name = "local_path") val localPath: String,
    @ColumnInfo(name = "firebase_path") val firebasePath: String? = null,
    @ColumnInfo(name = "synced") val synced: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
