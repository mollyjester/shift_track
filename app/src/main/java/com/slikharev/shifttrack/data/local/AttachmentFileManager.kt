package com.slikharev.shifttrack.data.local

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages attachment files in internal storage.
 *
 * Storage layout: `{filesDir}/attachments/{userId}/{date}/{uuid}.{ext}`
 *
 * Files stored here are private to the app and not accessible to other apps
 * unless exposed via [FileProvider].
 */
@Singleton
class AttachmentFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class FileInfo(
        val localRelativePath: String,
        val sizeBytes: Long,
        val originalFileName: String,
    )

    /**
     * Copies a file from [sourceUri] into internal storage under the
     * `attachments/{userId}/{date}/` directory.
     */
    suspend fun saveFile(
        userId: String,
        date: String,
        sourceUri: Uri,
        mimeType: String,
        displayName: String,
    ): FileInfo = withContext(Dispatchers.IO) {
        val ext = extensionFromMime(mimeType) ?: extensionFromName(displayName) ?: "bin"
        val uniqueName = "${UUID.randomUUID()}.$ext"
        val relativePath = "attachments/$userId/$date/$uniqueName"
        val destFile = File(context.filesDir, relativePath)
        destFile.parentFile?.mkdirs()

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalArgumentException("Cannot open URI: $sourceUri")

        FileInfo(
            localRelativePath = relativePath,
            sizeBytes = destFile.length(),
            originalFileName = displayName,
        )
    }

    /**
     * Saves an already-compressed photo file into internal storage.
     */
    suspend fun saveCompressedPhoto(
        userId: String,
        date: String,
        compressedFile: File,
        originalName: String,
    ): FileInfo = withContext(Dispatchers.IO) {
        val uniqueName = "${UUID.randomUUID()}.jpg"
        val relativePath = "attachments/$userId/$date/$uniqueName"
        val destFile = File(context.filesDir, relativePath)
        destFile.parentFile?.mkdirs()

        compressedFile.copyTo(destFile, overwrite = true)
        compressedFile.delete()

        FileInfo(
            localRelativePath = relativePath,
            sizeBytes = destFile.length(),
            originalFileName = originalName,
        )
    }

    /** Resolves a relative path to an absolute [File]. */
    fun getFile(localPath: String): File = File(context.filesDir, localPath)

    /** Deletes a single attachment file. */
    suspend fun deleteFile(localPath: String) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, localPath)
        file.delete()
    }

    /** Deletes all attachment files for a user. */
    suspend fun deleteFilesForUser(userId: String) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "attachments/$userId")
        dir.deleteRecursively()
    }

    /** Batch-deletes attachment files by their relative paths. */
    suspend fun deleteFiles(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.forEach { path ->
            File(context.filesDir, path).delete()
        }
    }

    private fun extensionFromMime(mimeType: String): String? =
        MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)

    private fun extensionFromName(name: String): String? =
        name.substringAfterLast('.', "").takeIf { it.isNotEmpty() }
}
