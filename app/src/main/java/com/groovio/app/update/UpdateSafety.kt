package com.groovio.app.update

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.groovio.app.GroovioApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Updating Groovio must never require deleting its app data. Before each update,
 * export a separate, user-recoverable JSON snapshot into public Downloads.
 * Audio download files are NOT included and must remain in app storage.
 */
object UpdateSafety {
    internal fun isSafeUpgrade(
        installedPackage: String,
        candidatePackage: String?,
        installedVersion: Long,
        candidateVersion: Long,
        installedSigners: Set<String>,
        candidateSigners: Set<String>,
    ): Boolean = installedPackage == candidatePackage &&
        candidateVersion > installedVersion &&
        installedSigners.isNotEmpty() && installedSigners == candidateSigners

    suspend fun saveLibraryBackup(context: Context): String = withContext(Dispatchers.IO) {
        val app = context.applicationContext as GroovioApplication
        // Same format as Settings > Back up library, including collection history,
        // spoken progress and preferences; the existing restore route can read it.
        val text = JSONObject(app.library.exportBackup())
            .put("settings", app.settings.export())
            .put("spoken", app.spoken.export())
            .put("spokenCollections", app.spoken.exportCollections())
            .put("collectionHistory", app.collectionHistory.export())
            .toString(2)
        if (text.toByteArray(Charsets.UTF_8).size > 8 * 1024 * 1024)
            throw IOException("Library is too large for the JSON backup. Export it from Settings before updating.")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            throw IOException("On Android 8 or 9, export your library in Settings before updating. Update blocked to avoid data loss.")

        // Scoped-storage Downloads files survive app updates and uninstalls.
        val name = "groovio-library-" + SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date()) + ".json"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Groovio/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Couldn't create library backup in Downloads; update cancelled.")
        try {
            resolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(text)
                writer.flush()
            } ?: throw IOException("Couldn't write library backup")
            val complete = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (resolver.update(uri, complete, null, null) < 1)
                throw IOException("Couldn't finish library backup")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw IOException("Couldn't save library backup; update cancelled. " + (e.message ?: ""), e)
        }
        "Downloads/Groovio/" + name
    }
}
