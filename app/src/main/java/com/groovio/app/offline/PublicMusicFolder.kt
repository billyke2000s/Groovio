package com.groovio.app.offline

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.groovio.app.data.TrackEntity
import java.io.File
import java.io.IOException

/**
 * Public Music/Groovio folder, shared with the device Files app.
 * Scoped MediaStore writes on Android 10+ never need all-files access.
 * Private downloaded originals remain unchanged until confirmed phone playback.
 */
class PublicMusicFolder(private val context: Context) {
    private val resolver = context.contentResolver
    private val exports = context.getSharedPreferences("shared_music_exports", Context.MODE_PRIVATE)

    companion object {
        const val RELATIVE_PATH = "Music/Groovio/"
        private val allowedExtensions = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav", "webm", "mp4")

        fun available(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        fun permission(): String? = when {
            Build.VERSION.SDK_INT >= 33 -> Manifest.permission.READ_MEDIA_AUDIO
            Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 33 -> Manifest.permission.READ_EXTERNAL_STORAGE
            else -> null
        }
        fun hasScanPermission(context: Context): Boolean {
            val permission = permission() ?: return true
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        internal fun cleanName(text: String): String =
            text.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "-").trim(' ', '.').take(85).ifBlank { "Audio" }

        /** The actual file signature takes precedence over a codec description. */
        internal fun formatFor(file: File, codec: String? = null): Pair<String, String> {
            val header = file.inputStream().use { it.readNBytes(16) }
            val start = header.toString(Charsets.ISO_8859_1)
            return when {
                start.startsWith("ID3") || (header.size >= 2 && header[0].toInt() and 0xff == 0xff &&
                    header[1].toInt() and 0xe0 == 0xe0) -> "mp3" to "audio/mpeg"
                start.startsWith("OggS") -> "ogg" to "audio/ogg"
                start.startsWith("fLaC") -> "flac" to "audio/flac"
                start.startsWith("RIFF") -> "wav" to "audio/wav"
                header.size >= 8 && start.substring(4, 8) == "ftyp" -> "m4a" to "audio/mp4"
                header.size >= 4 && header.take(4).map { it.toInt() and 0xff } == listOf(0x1a, 0x45, 0xdf, 0xa3) ->
                    "webm" to "audio/webm"
                codec.orEmpty().contains("opus", true) -> "webm" to "audio/webm"
                codec.orEmpty().contains("vorbis", true) -> "ogg" to "audio/ogg"
                codec.orEmpty().contains("mp4", true) || codec.orEmpty().contains("aac", true) -> "m4a" to "audio/mp4"
                else -> "webm" to "audio/webm"
            }
        }
    }

    /** A downloaded track can be re-exported after user deletion; existing copies aren't overwritten. */
    @Synchronized fun publish(track: TrackEntity, privateFile: File, codec: String? = null): Uri? {
        if (!available()) return null
        if (!privateFile.isFile || privateFile.length() <= 0) throw IOException("Downloaded audio is incomplete")
        val saved = exports.getString(track.mediaId, null)?.let(Uri::parse)
        if (saved != null && runCatching {
                resolver.query(saved, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use {
                    it.moveToFirst() && it.getLong(0) == privateFile.length()
                } ?: false
            }.getOrDefault(false)) return saved

        val (extension, mime) = formatFor(privateFile, codec)
        val identity = cleanName(track.mediaId.removePrefix("yt:"))
        val name = cleanName(listOfNotNull(track.artist, track.title).joinToString(" - ")) +
            " [$identity].$extension"
        val uri = createItem(name, mime, track.title, track.artist)
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                privateFile.inputStream().use { it.copyTo(output) }
            } ?: throw IOException("Couldn't open public music file")
            finishItem(uri)
            exports.edit().putString(track.mediaId, uri.toString()).commit()
            return uri
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw IOException("Couldn't copy downloaded song to Music/Groovio", error)
        }
    }

    /**
     * Uses the standard Android file picker URI only while copying; source files
     * stay untouched, and imported copies can be played across app restarts.
     */
    fun importAudio(source: Uri): Uri {
        if (!available()) throw IOException("Public music folder needs Android 10 or newer")
        val supplied = resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Imported audio"
        val name = cleanName(supplied.substringBeforeLast('.', supplied))
        val extension = supplied.substringAfterLast('.', "").lowercase()
        if (extension !in allowedExtensions) throw IOException("Unsupported audio file: $supplied")
        val mime = when (extension) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            "webm" -> "audio/webm"
            "aac" -> "audio/aac"
            else -> "audio/mp4"
        }
        val target = createItem("$name.$extension", mime, name, null)
        try {
            val input = resolver.openInputStream(source) ?: throw IOException("Couldn't read selected audio")
            input.use { stream ->
                val output = resolver.openOutputStream(target, "w") ?: throw IOException("Couldn't create imported file")
                output.use {
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        copied += n
                        if (copied > 1024L * 1024 * 1024) throw IOException("File exceeds 1 GB")
                        it.write(buffer, 0, n)
                    }
                    if (copied == 0L) throw IOException("Empty audio file")
                }
            }
            finishItem(target)
            return target
        } catch (error: Exception) {
            runCatching { resolver.delete(target, null, null) }
            throw IOException("Couldn't import $supplied", error)
        }
    }

    /** Scans tracks, including MP3s copied into Music/Groovio using another file manager. */
    fun tracks(): List<TrackEntity> {
        if (!available()) return emptyList()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        val rows = mutableListOf<TrackEntity>()
        resolver.query(uri, columns, "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.IS_PENDING}=0",
            arrayOf(RELATIVE_PATH), "${MediaStore.Audio.Media.DISPLAY_NAME} COLLATE NOCASE ASC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val fileCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(fileCol) ?: continue
                if (displayName.substringAfterLast('.', "").lowercase() !in allowedExtensions) continue
                val title = cursor.getString(titleCol).orEmpty().takeIf { it.isNotBlank() && it != "<unknown>" }
                    ?: displayName.substringBeforeLast('.')
                val artist = cursor.getString(artistCol).takeUnless { it.isNullOrBlank() || it == "<unknown>" }
                rows += TrackEntity(
                    ContentUris.withAppendedId(uri, cursor.getLong(idCol)).toString(),
                    title, artist, null)
            }
        }
        return rows
    }

    private fun createItem(name: String, mime: String, title: String, artist: String?): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(MediaStore.Audio.Media.TITLE, title)
            artist?.let { put(MediaStore.Audio.Media.ARTIST, it) }
        }
        return resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Couldn't create Music/Groovio file")
    }
    private fun finishItem(uri: Uri) {
        if (resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null, null) < 1) throw IOException("Couldn't finish public audio file")
    }
}
