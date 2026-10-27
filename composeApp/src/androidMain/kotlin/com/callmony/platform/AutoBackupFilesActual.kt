package com.callmony.platform

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore

/* Android actual of the v1.6.34 auto-backup layer — MediaStore.Downloads.
   One file, Downloads/Callmony/callmony-backup.json, written in place
   (query by display name first, insert once, update after) so the folder
   never fills with copies. No storage permission is needed for the app's
   OWN contributions on API 29+, and because MediaStore rows carry the
   contributing package name, a REINSTALLED app (same package) owns the
   row again and can read the file back without any prompt. All calls run
   on background threads (the caller is the Persist IO queue). */

actual object AutoBackupFiles {

    private const val DISPLAY_NAME = "callmony-backup.json"
    private const val RELATIVE_PATH = "Download/Callmony"

    private fun ctx(): Context? {
        if (!com.callmony.AndroidPlatform.isReady()) return null
        return com.callmony.AndroidPlatform.appContext
    }

    private fun existingUri(ctx: Context): android.net.Uri? = try {
        ctx.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(DISPLAY_NAME),
            null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }?.let { id ->
            android.net.Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
        }
    } catch (_: Exception) { null }

    actual fun write(text: String): Boolean {
        val ctx = ctx() ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false // MediaStore.Downloads is API 29+
        return try {
            val bytes = text.toByteArray(Charsets.UTF_8)
            val resolver = ctx.contentResolver
            val uri = existingUri(ctx) ?: resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, DISPLAY_NAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
                },
            ) ?: return false
            // "wt" truncates — the whole file is rewritten every time
            resolver.openOutputStream(uri, "wt")?.use { os ->
                os.write(bytes)
                os.flush()
            } ?: return false
            true
        } catch (_: Exception) { false }
    }

    actual fun read(): String? {
        val ctx = ctx() ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val uri = existingUri(ctx) ?: return null
            ctx.contentResolver.openInputStream(uri)?.use { ins ->
                ins.readBytes().toString(Charsets.UTF_8)
            }
        } catch (_: Exception) { null }
    }

    actual fun delete() {
        val ctx = ctx() ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            existingUri(ctx)?.let { ctx.contentResolver.delete(it, null, null) }
        } catch (_: Exception) { }
    }
}
