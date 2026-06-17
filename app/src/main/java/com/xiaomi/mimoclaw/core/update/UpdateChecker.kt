package com.xiaomi.mimoclaw.core.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub Release 更新检测器
 *
 * 通过 GitHub API 检查最新 Release，比较版本号判断是否需要更新。
 * 版本格式优先级: v{x}.{y}.{z} (语义版本) > build-{N} (CI 构建号)
 *
 * 对于 build-{N} 格式，仅当 remote > current 且差值 >= 1 时才提示更新，
 * 避免 CI 频繁构建导致的误报。
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val gson: Gson
) {
    companion object {
        private const val REPO = "feihu1991/MiMoClaw"
        private const val LATEST_URL =
            "https://api.github.com/repos/$REPO/releases/latest"
        private const val CONNECT_TIMEOUT = 10L
        private const val READ_TIMEOUT = 15L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .build()

    /**
     * 检查是否有新版本
     *
     * @param currentVersionCode 当前 app 的 versionCode (来自 build.gradle)
     * @return UpdateInfo 如果有更新; null 如果已是最新
     */
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(LATEST_URL)
                    .header("Accept", "application/vnd.github+json")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val body = response.body?.string() ?: return@withContext null
                val release = gson.fromJson(body, GitHubRelease::class.java)

                val remoteVersion = parseVersion(release.tagName)
                    ?: return@withContext null

                // 同格式比较: 语义版本 vs 语义版本，CI 号 vs CI 号
                if (!remoteVersion.shouldUpdate(currentVersionCode)) {
                    return@withContext null
                }

                // 找到 APK 下载链接 (优先 release 包)
                val apkAsset = release.assets?.find {
                    it.name.contains("app-release.apk")
                } ?: release.assets?.find {
                    it.name.endsWith(".apk")
                }

                UpdateInfo(
                    versionCode = remoteVersion.code,
                    versionName = release.name ?: release.tagName,
                    releaseNotes = release.body ?: "无更新说明",
                    downloadUrl = apkAsset?.browserDownloadUrl ?: "",
                    apkSize = apkAsset?.size ?: 0,
                    publishedAt = release.publishedAt ?: "",
                    tagName = release.tagName
                )
            } catch (e: Exception) {
                null
            }
        }

    /**
     * 解析版本 tag，返回统一的版本信息
     * 优先识别语义版本 (v3.0.0)，其次识别 CI 构建号 (build-27)
     */
    private fun parseVersion(tag: String): ParsedVersion? {
        // 优先: 语义版本 v{major}.{minor}.{patch}
        val semver = Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)").find(tag)
        if (semver != null) {
            val major = semver.groupValues[1].toIntOrNull() ?: return null
            val minor = semver.groupValues[2].toIntOrNull() ?: return null
            val patch = semver.groupValues[3].toIntOrNull() ?: return null
            return ParsedVersion(
                code = major * 10000 + minor * 100 + patch,
                format = VersionFormat.SEMVER
            )
        }

        // 备选: CI 构建号 build-{number}
        val build = Regex("build-(\\d+)").find(tag)
        if (build != null) {
            val num = build.groupValues[1].toIntOrNull() ?: return null
            return ParsedVersion(
                code = num,
                format = VersionFormat.BUILD_NUMBER
            )
        }

        return null
    }
}

/** 解析后的版本信息 */
private data class ParsedVersion(
    val code: Int,
    val format: VersionFormat
) {
    /**
     * 判断是否应该更新
     * - 语义版本: code > current 即可
     * - CI 构建号: code > current 且差值 >= 2 (避免小版本迭代误报)
     */
    fun shouldUpdate(currentVersionCode: Int): Boolean {
        return when (format) {
            VersionFormat.SEMVER -> code > currentVersionCode
            VersionFormat.BUILD_NUMBER -> code > currentVersionCode + 1
        }
    }
}

private enum class VersionFormat {
    SEMVER,       // v3.0.0 → 30000
    BUILD_NUMBER  // build-27 → 27
}

// ── 数据模型 ──

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkSize: Long,
    val publishedAt: String,
    val tagName: String
) {
    fun formatSize(): String {
        if (apkSize <= 0) return "未知"
        val mb = apkSize / (1024.0 * 1024.0)
        return "%.1f MB".format(mb)
    }

    fun formatPublishedAt(): String {
        return try {
            publishedAt.substring(0, 10)
        } catch (e: Exception) {
            publishedAt
        }
    }
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
    @SerializedName("size") val size: Long
)
