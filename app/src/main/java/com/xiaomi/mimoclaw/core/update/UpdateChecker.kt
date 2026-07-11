package com.xiaomi.mimoclaw.core.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    @Named("plain") private val client: OkHttpClient,
    private val gson: Gson
) {
    suspend fun checkForUpdate(
        currentVersionCode: Int,
        currentVersionName: String
    ): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("GitHub 更新检查失败: HTTP ${response.code}")
                val body = response.body?.string() ?: error("GitHub 返回空响应")
                val release = gson.fromJson(body, GitHubRelease::class.java)
                val remote = VersionPolicy.parse(release.tagName)
                    ?: error("无法识别版本标签: ${release.tagName}")
                if (!VersionPolicy.shouldUpdate(remote, currentVersionCode, currentVersionName)) {
                    return@use null
                }

                val apk = release.assets?.firstOrNull { it.name.contains("app-release.apk") }
                    ?: release.assets?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: error("发布版本缺少 APK 资产")

                UpdateInfo(
                    versionCode = remote.code,
                    versionName = release.name ?: release.tagName,
                    releaseNotes = release.body ?: "无更新说明",
                    downloadUrl = apk.browserDownloadUrl,
                    apkSize = apk.size,
                    publishedAt = release.publishedAt.orEmpty(),
                    tagName = release.tagName,
                    sha256 = apk.digest
                        ?.substringAfter("sha256:", missingDelimiterValue = "")
                        ?.lowercase()
                        ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
                )
            }
        }
    }

    private companion object {
        const val REPO = "feihu1991/MiMoClaw"
        const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    }
}

object VersionPolicy {
    sealed interface ParsedVersion {
        val code: Int

        data class Semantic(
            val major: Int,
            val minor: Int,
            val patch: Int
        ) : ParsedVersion {
            override val code: Int = major * 10_000 + minor * 100 + patch
        }

        data class Build(override val code: Int) : ParsedVersion
    }

    fun parse(tag: String): ParsedVersion? {
        SEMVER.matchEntire(tag.trim())?.let { match ->
            return ParsedVersion.Semantic(
                match.groupValues[1].toIntOrNull() ?: return null,
                match.groupValues[2].toIntOrNull() ?: return null,
                match.groupValues[3].toIntOrNull() ?: return null
            )
        }
        BUILD.matchEntire(tag.trim())?.let { match ->
            return ParsedVersion.Build(match.groupValues[1].toIntOrNull() ?: return null)
        }
        return null
    }

    fun shouldUpdate(
        remote: ParsedVersion,
        currentVersionCode: Int,
        currentVersionName: String
    ): Boolean = when (remote) {
        is ParsedVersion.Build -> remote.code > currentVersionCode
        is ParsedVersion.Semantic -> {
            val current = parse(currentVersionName) as? ParsedVersion.Semantic
            current == null || compareValuesBy(
                remote,
                current,
                ParsedVersion.Semantic::major,
                ParsedVersion.Semantic::minor,
                ParsedVersion.Semantic::patch
            ) > 0
        }
    }

    private val SEMVER = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$")
    private val BUILD = Regex("^build-(\\d+)$")
}

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkSize: Long,
    val publishedAt: String,
    val tagName: String,
    val sha256: String?
) {
    fun formatSize(): String = if (apkSize <= 0) {
        "未知"
    } else {
        "%.1f MB".format(apkSize / (1024.0 * 1024.0))
    }

    fun formatPublishedAt(): String = publishedAt.take(10)
}

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("published_at") val publishedAt: String?,
    @SerializedName("assets") val assets: List<GitHubAsset>?
)

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    @SerializedName("size") val size: Long,
    @SerializedName("digest") val digest: String?
)
