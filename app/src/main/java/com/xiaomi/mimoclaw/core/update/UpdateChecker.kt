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
 * Release 格式要求: tag_name = "build-{runNumber}" 或 "v{version}"
 * APK 命名: app-debug.apk 或 app-release.apk
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
     * @param currentVersionCode 当前 app 的 versionCode
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

                // 解析版本号: 支持 "build-27" 或 "v3.0.0" 格式
                val remoteVersionCode = extractVersionCode(release.tagName)
                    ?: return@withContext null

                if (remoteVersionCode <= currentVersionCode) {
                    return@withContext null // 已是最新
                }

                // 找到 APK 下载链接 (优先 release 包)
                val apkAsset = release.assets?.find {
                    it.name.contains("app-release.apk")
                } ?: release.assets?.find {
                    it.name.endsWith(".apk")
                }

                UpdateInfo(
                    versionCode = remoteVersionCode,
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
     * 从 tag 提取版本号
     * "build-27" -> 27
     * "v3.0.0" -> 基于 major*10000 + minor*100 + patch 计算
     */
    private fun extractVersionCode(tag: String): Int? {
        // 格式1: build-{number}
        val buildMatch = Regex("build-(\\d+)").find(tag)
        if (buildMatch != null) {
            return buildMatch.groupValues[1].toIntOrNull()
        }

        // 格式2: v{major}.{minor}.{patch}
        val versionMatch = Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)").find(tag)
        if (versionMatch != null) {
            val major = versionMatch.groupValues[1].toIntOrNull() ?: return null
            val minor = versionMatch.groupValues[2].toIntOrNull() ?: return null
            val patch = versionMatch.groupValues[3].toIntOrNull() ?: return null
            return major * 10000 + minor * 100 + patch
        }

        return null
    }
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
    /** 格式化文件大小 */
    fun formatSize(): String {
        if (apkSize <= 0) return "未知"
        val mb = apkSize / (1024.0 * 1024.0)
        return "%.1f MB".format(mb)
    }

    /** 格式化发布时间 */
    fun formatPublishedAt(): String {
        return try {
            // "2026-06-17T05:00:07Z" -> "2026-06-17"
            publishedAt.substring(0, 10)
        } catch (e: Exception) {
            publishedAt
        }
    }
}

/** GitHub API Release 响应 */
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
