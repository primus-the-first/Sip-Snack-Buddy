package com.sipsnack.buddy.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.sipsnack.buddy.BuildConfig
import com.sipsnack.buddy.rules.Versions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Release(val version: String, val apkUrl: String)

/**
 * One-tap updates from the repo's public GitHub Releases: check the latest release, download its APK,
 * and hand it to Android's installer. Android always asks her to confirm; nothing installs silently.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/primus-the-first/Sip-Snack-Buddy/releases/latest"

    /** The newest release if it is newer than this install, else null. Null on any network trouble too. */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val json = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val tag = json.getString("tag_name")
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@runCatching null
            if (Versions.isNewer(tag, BuildConfig.VERSION_NAME)) Release(tag.removePrefix("v"), apk.getString("browser_download_url")) else null
        }.getOrNull()
    }

    /** Android asks once whether this app may install updates; until then, send her to that switch. */
    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Downloads the APK with the system downloader, waits for it, then opens the installer. */
    suspend fun downloadAndInstall(context: Context, release: Release): Boolean = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return@withContext false
        val file = File(dir, "SipSnack-${release.version}.apk")
        if (file.exists()) file.delete()
        val dm = context.getSystemService(DownloadManager::class.java)
        val id = dm.enqueue(
            DownloadManager.Request(Uri.parse(release.apkUrl))
                .setTitle("Sip & Snack ${release.version}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, file.name),
        )
        while (true) {
            delay(500)
            val status = dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (!c.moveToFirst()) DownloadManager.STATUS_FAILED
                else c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            }
            if (status == DownloadManager.STATUS_SUCCESSFUL) break
            if (status == DownloadManager.STATUS_FAILED) return@withContext false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        withContext(Dispatchers.Main) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        true
    }
}
