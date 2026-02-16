package com.fdroid.cryptomonitor.update

import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubReleaseApi {

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubReleaseDto
}

data class GitHubReleaseDto(
    val tag_name: String,
    val name: String?,
    val body: String?,
    val html_url: String,
    val assets: List<GitHubReleaseAssetDto>
)

data class GitHubReleaseAssetDto(
    val name: String,
    val browser_download_url: String
)
