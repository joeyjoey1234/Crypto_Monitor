package com.fdroid.cryptomonitor.update

data class AppUpdateInfo(
    val versionTag: String,
    val releaseName: String,
    val changelog: String,
    val apkFileName: String,
    val apkDownloadUrl: String,
    val releasePageUrl: String
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult()
    data class NoUpdate(val latestTag: String) : UpdateCheckResult()
    data class Failed(val reason: String) : UpdateCheckResult()
}
