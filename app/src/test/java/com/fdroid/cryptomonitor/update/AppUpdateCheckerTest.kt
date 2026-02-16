package com.fdroid.cryptomonitor.update

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun `returns update available when release is newer and has apk`() = runTest {
        val api = FakeReleaseApi(
            release = GitHubReleaseDto(
                tag_name = "v1.2.0",
                name = "v1.2.0",
                body = "changes",
                html_url = "https://example.com/release",
                assets = listOf(
                    GitHubReleaseAssetDto("app-release.apk", "https://example.com/app.apk")
                )
            )
        )
        val checker = AppUpdateChecker(api, "owner", "repo")

        val result = checker.checkForUpdate("1.0.0")

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val available = result as UpdateCheckResult.UpdateAvailable
        assertEquals("v1.2.0", available.info.versionTag)
    }

    @Test
    fun `returns no update when release is not newer`() = runTest {
        val api = FakeReleaseApi(
            release = GitHubReleaseDto(
                tag_name = "v1.0.0",
                name = "v1.0.0",
                body = null,
                html_url = "https://example.com/release",
                assets = listOf(
                    GitHubReleaseAssetDto("app-release.apk", "https://example.com/app.apk")
                )
            )
        )
        val checker = AppUpdateChecker(api, "owner", "repo")

        val result = checker.checkForUpdate("1.0.0")

        assertTrue(result is UpdateCheckResult.NoUpdate)
    }

    @Test
    fun `returns failure when latest release has no apk asset`() = runTest {
        val api = FakeReleaseApi(
            release = GitHubReleaseDto(
                tag_name = "v2.0.0",
                name = "v2.0.0",
                body = null,
                html_url = "https://example.com/release",
                assets = listOf(
                    GitHubReleaseAssetDto("notes.txt", "https://example.com/notes.txt")
                )
            )
        )
        val checker = AppUpdateChecker(api, "owner", "repo")

        val result = checker.checkForUpdate("1.0.0")

        assertTrue(result is UpdateCheckResult.Failed)
    }

    private class FakeReleaseApi(
        private val release: GitHubReleaseDto
    ) : GitHubReleaseApi {
        override suspend fun getLatestRelease(owner: String, repo: String): GitHubReleaseDto = release
    }
}
