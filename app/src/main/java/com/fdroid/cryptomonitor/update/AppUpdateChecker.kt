package com.fdroid.cryptomonitor.update

class AppUpdateChecker(
    private val releaseApi: GitHubReleaseApi,
    private val owner: String,
    private val repo: String
) {

    suspend fun checkForUpdate(currentVersionName: String): UpdateCheckResult {
        val release = runCatching {
            releaseApi.getLatestRelease(owner = owner, repo = repo)
        }.getOrElse { error ->
            return UpdateCheckResult.Failed(error.message ?: "Could not reach GitHub releases")
        }

        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return UpdateCheckResult.Failed("Latest release does not contain an APK asset")

        if (!VersionComparator.isNewer(release.tag_name, currentVersionName)) {
            return UpdateCheckResult.NoUpdate(release.tag_name)
        }

        val info = AppUpdateInfo(
            versionTag = release.tag_name,
            releaseName = release.name ?: release.tag_name,
            changelog = release.body.orEmpty(),
            apkFileName = apk.name,
            apkDownloadUrl = apk.browser_download_url,
            releasePageUrl = release.html_url
        )
        return UpdateCheckResult.UpdateAvailable(info)
    }
}
