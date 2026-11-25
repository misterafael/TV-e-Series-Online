package br.com.tveseriesonline

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateChecker {
    private const val API_URL = "https://api.github.com/repos/misterafael/TV-e-Series-Online/releases/latest"
    private const val PREFS = "app_update_preferences"
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val KEY_PENDING_URI = "pending_apk_uri"
    private const val KEY_LAST_CHECK = "last_check"
    private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L

    private data class Release(val version: String, val downloadUrl: String, val fileName: String)

    fun checkOnLaunch(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

        CoroutineScope(Dispatchers.IO).launch {
            val release = runCatching { fetchLatestRelease() }.getOrNull() ?: return@launch
            val current = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "1.0.0"
            if (!isNewer(release.version, current)) return@launch

            withContext(Dispatchers.Main) {
                if (activity.isFinishing || activity.isDestroyed) return@withContext
                download(activity, release)
            }
        }
    }

    fun resumeInstallIfReady(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val uriText = prefs.getString(KEY_PENDING_URI, null) ?: return
        val uri = Uri.parse(uriText)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(settingsIntent)
            return
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (installIntent.resolveActivity(activity.packageManager) != null) {
            prefs.edit().remove(KEY_PENDING_URI).apply()
            activity.startActivity(installIntent)
        } else {
            Toast.makeText(activity, "Não foi possível abrir o instalador do Android.", Toast.LENGTH_LONG).show()
        }
    }

    private fun download(context: Context, release: Release) {
        val request = DownloadManager.Request(Uri.parse(release.downloadUrl))
            .setTitle("Atualização TV e Séries Online")
            .setDescription("Baixando versão ${release.version}")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, release.fileName)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = manager.enqueue(request)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_DOWNLOAD_ID, id).apply()
        Toast.makeText(context, "Download iniciado. O Android solicitará sua confirmação para instalar.", Toast.LENGTH_LONG).show()
    }

    fun onDownloadCompleted(context: Context, completedId: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_DOWNLOAD_ID, -1L) != completedId) return
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = manager.getUriForDownloadedFile(completedId) ?: return
        prefs.edit().putString(KEY_PENDING_URI, uri.toString()).remove(KEY_DOWNLOAD_ID).apply()
        Toast.makeText(context, "Atualização baixada. Abra o app para concluir a instalação.", Toast.LENGTH_LONG).show()
    }

    private fun fetchLatestRelease(): Release {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "TV-e-Series-Online-Android")
        }
        try {
            if (connection.responseCode !in 200..299) error("GitHub HTTP ${connection.responseCode}")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name").removePrefix("v")
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    return Release(tag, asset.getString("browser_download_url"), name)
                }
            }
            error("A última versão do GitHub ainda não contém um APK.")
        } finally {
            connection.disconnect()
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        fun parts(value: String) = value.split(".").map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    class DownloadReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                AppUpdateChecker.onDownloadCompleted(context, intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L))
            }
        }
    }
}
