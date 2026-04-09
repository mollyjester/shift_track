package com.slikharev.shifttrack.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compresses photos that exceed [MAX_FILE_SIZE] (3 MB) using a binary-search
 * over JPEG quality, then falls back to downscaling if quality 10 is still too large.
 *
 * EXIF orientation is applied before compression so the output is always upright.
 */
@Singleton
class PhotoCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val MAX_FILE_SIZE: Long = 3L * 1024 * 1024 // 3 MB
        private const val MIN_QUALITY = 10
        private const val MAX_QUALITY = 95
    }

    /**
     * If the image at [sourceUri] exceeds [MAX_FILE_SIZE], compress it to the best
     * JPEG quality that fits within the limit. Returns a temp file in the cache dir.
     * Non-image URIs or images already under the limit are copied as-is.
     */
    suspend fun compressIfNeeded(sourceUri: Uri): File = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalArgumentException("Cannot open URI: $sourceUri")

        val rawBytes = inputStream.use { it.readBytes() }

        if (rawBytes.size <= MAX_FILE_SIZE) {
            val out = createTempFile()
            out.writeBytes(rawBytes)
            return@withContext out
        }

        // Decode with orientation fix
        val bitmap = decodeBitmapWithOrientation(sourceUri, rawBytes)
            ?: throw IllegalArgumentException("Cannot decode image: $sourceUri")

        // Binary-search best JPEG quality that fits within the limit
        var result = compressAtQuality(bitmap, MAX_QUALITY)
        if (result.size <= MAX_FILE_SIZE) {
            return@withContext writeTempFile(result)
        }

        var low = MIN_QUALITY
        var high = MAX_QUALITY
        var bestQuality = MIN_QUALITY

        while (low <= high) {
            val mid = (low + high) / 2
            result = compressAtQuality(bitmap, mid)
            if (result.size <= MAX_FILE_SIZE) {
                bestQuality = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        result = compressAtQuality(bitmap, bestQuality)
        if (result.size <= MAX_FILE_SIZE) {
            return@withContext writeTempFile(result)
        }

        // Fallback: scale down until it fits
        var scaled = bitmap
        var attempt = 0
        while (attempt < 5) {
            scaled = Bitmap.createScaledBitmap(
                scaled,
                (scaled.width * 0.7).toInt().coerceAtLeast(1),
                (scaled.height * 0.7).toInt().coerceAtLeast(1),
                true,
            )
            result = compressAtQuality(scaled, bestQuality)
            if (result.size <= MAX_FILE_SIZE) {
                return@withContext writeTempFile(result)
            }
            attempt++
        }

        // Last resort: return whatever we have
        writeTempFile(result)
    }

    private fun decodeBitmapWithOrientation(uri: Uri, rawBytes: ByteArray): Bitmap? {
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null

        val rotation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) {
            0f
        }

        if (rotation == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun compressAtQuality(bitmap: Bitmap, quality: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return baos.toByteArray()
    }

    private fun createTempFile(): File =
        File.createTempFile("attachment_", ".tmp", context.cacheDir)

    private fun writeTempFile(bytes: ByteArray): File {
        val file = createTempFile()
        file.writeBytes(bytes)
        return file
    }
}
