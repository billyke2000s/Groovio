package com.groovio.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Downloads a Groovio update inside the app and hands it to Android's installer.
 * Android always shows its own "update this app?" confirmation for apps installed
 * outside Google Play; that single tap cannot be skipped.
 */
class UpdateInstaller(
    private val context: Context,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val dir get() = File(context.cacheDir, "updates")

    /** Downloads the APK, reporting progress 0..1 (or -1 when the size is unknown). */
    suspend fun download(update: AvailableUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "groovio-update.apk")
        http.newCall(Request.Builder().url(update.apkUrl).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Download failed (HTTP ${r.code})")
            val body = r.body ?: throw IOException("Empty download")
            val total = body.contentLength()
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        onProgress(if (total > 0) done.toFloat() / total else -1f)
                    }
                }
            }
        }
        file
    }

    /**
     * Never let the system installer see a different app, signer, or a downgrade.
     * Only a verified in-place upgrade can keep Android's private app data.
     * A recoverable copy is saved outside the app sandbox before proceeding.
     */
    suspend fun prepareUpdate(file: File): String {
        withContext(Dispatchers.IO) { verifySameAppUpgrade(file) }
        return UpdateSafety.saveLibraryBackup(context)
    }

    @Suppress("DEPRECATION")
    private fun verifySameAppUpgrade(file: File) {
        if (!file.isFile || file.length() == 0L) throw IOException("Incomplete update APK")
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = pm.getPackageInfo(context.packageName, flags)
        val candidate = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IOException("The downloaded file isn't a valid APK")
        fun signers(info: PackageInfo): Set<String> {
            val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                info.signingInfo?.apkContentsSigners else info.signatures
            return certificates?.map { it.toCharsString() }?.toSet().orEmpty()
        }
        val installedVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            installed.longVersionCode else installed.versionCode.toLong()
        val candidateVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            candidate.longVersionCode else candidate.versionCode.toLong()
        if (!UpdateSafety.isSafeUpgrade(
                context.packageName, candidate.packageName,
                installedVersion, candidateVersion, signers(installed), signers(candidate)
            )
        ) throw IOException("Unsafe update blocked: different package, signature, or older version. Keep the installed app; do not uninstall.")
    }

    /** Whether Android allows Groovio to install packages (asked once, in system settings). */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" switch for Groovio. */
    fun openInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Shows Android's install/update screen for the downloaded APK. */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
