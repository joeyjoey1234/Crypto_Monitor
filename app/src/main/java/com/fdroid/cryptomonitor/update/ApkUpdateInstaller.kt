package com.fdroid.cryptomonitor.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object ApkUpdateInstaller {

    private val httpClient = OkHttpClient.Builder().build()

    suspend fun downloadApk(context: Context, updateInfo: AppUpdateInfo): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(updateInfo.apkDownloadUrl)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            error("Download failed: HTTP ${response.code}")
        }

        val body = response.body ?: run {
            response.close()
            error("Download failed: empty response")
        }

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val outputFile = File(updatesDir, updateInfo.apkFileName)

        body.byteStream().use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        response.close()
        outputFile
    }

    fun installApk(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(installIntent)
        } catch (e: ActivityNotFoundException) {
            error("No installer app available")
        }
    }
}
