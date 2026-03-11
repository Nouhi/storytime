package com.storytime.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

object FileHelper {
    private const val TAG = "FileHelper"

    /**
     * Save file to the device Downloads folder via MediaStore.
     * Returns a user-facing message (success or error).
     * This method does I/O — call from Dispatchers.IO.
     */
    fun saveToDownloads(context: Context, data: ByteArray, filename: String, mimeType: String): String {
        Log.d(TAG, "saveToDownloads: filename=$filename, mimeType=$mimeType, dataSize=${data.size}")

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ — use MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Storytime")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                if (uri == null) {
                    Log.e(TAG, "saveToDownloads: MediaStore insert returned null URI")
                    return "Failed to create file in Downloads"
                }

                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(data)
                    outputStream.flush()
                    Log.d(TAG, "saveToDownloads: wrote ${data.size} bytes to $uri")
                } ?: run {
                    Log.e(TAG, "saveToDownloads: could not open output stream for $uri")
                    return "Failed to write file"
                }

                // Mark as complete
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                Log.d(TAG, "saveToDownloads: file saved successfully to Downloads/Storytime/$filename")
                "Saved to Downloads/Storytime/$filename"

            } else {
                // Pre-Android 10 — write directly to Downloads folder
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Storytime"
                )
                downloadsDir.mkdirs()
                val file = File(downloadsDir, filename)
                file.writeBytes(data)
                Log.d(TAG, "saveToDownloads: wrote file to ${file.absolutePath}")
                "Saved to Downloads/Storytime/$filename"
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveToDownloads: failed", e)
            "Failed to save file: ${e.message}"
        }
    }

    fun sanitizeFilename(title: String): String {
        return title
            .replace(Regex("[^a-zA-Z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .take(60)
            .ifEmpty { "story" }
    }
}
