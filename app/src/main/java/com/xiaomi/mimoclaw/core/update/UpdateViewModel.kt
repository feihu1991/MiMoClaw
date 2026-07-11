package com.xiaomi.mimoclaw.core.update

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
    private val updatePrefs: UpdatePrefs,
    private val application: Application
) : ViewModel() {

    companion object {
        private const val TAG = "UpdateVM"
        private const val DOWNLOAD_TIMEOUT_MS = 10 * 60 * 1000L
        private const val POLL_INTERVAL_MS = 800L
        private const val MAX_EMPTY_POLLS = 15
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private var dismissedVersionCode: Int = -1

    init {
        viewModelScope.launch {
            dismissedVersionCode = updatePrefs.getDismissedVersionCode()
            checkUpdate()
        }
    }

    fun checkUpdate() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            try {
                val currentVersionCode = getCurrentVersionCode()
                val result = updateChecker.checkForUpdate(
                    currentVersionCode = currentVersionCode,
                    currentVersionName = getCurrentVersionName()
                )
                result.fold(
                    onSuccess = { info ->
                        if (info != null && info.versionCode != dismissedVersionCode) {
                            _updateInfo.value = info
                            _updateState.value = UpdateState.Available(info)
                        } else {
                            _updateState.value = UpdateState.UpToDate
                        }
                    },
                    onFailure = { error ->
                        _updateState.value = UpdateState.Error(error.message ?: "检查更新失败")
                    }
                )
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "检查更新失败")
            }
        }
    }

    fun startDownload() {
        val info = _updateInfo.value ?: return
        if (info.downloadUrl.isBlank()) {
            _updateState.value = UpdateState.Error("下载链接不可用")
            return
        }
        val downloadUri = runCatching { Uri.parse(info.downloadUrl) }.getOrNull()
        if (downloadUri?.scheme != "https" || downloadUri.host != "github.com") {
            _updateState.value = UpdateState.Error("更新下载地址不可信")
            return
        }
        if (info.sha256 == null) {
            _updateState.value = UpdateState.Error("发布资产缺少 SHA-256，已拒绝下载")
            return
        }

        viewModelScope.launch {
            try {
                _updateState.value = UpdateState.Downloading(0f)
                Log.d(TAG, "开始下载: ${info.downloadUrl}")

                val dm = application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

                // 清理旧文件
                val safeTag = info.tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val fileName = "mimo-agent-$safeTag.apk"
                val file = downloadFile(fileName)
                if (file.exists()) file.delete()

                val request = DownloadManager.Request(downloadUri).apply {
                    setTitle("MiMo Agent 更新")
                    setDescription("正在下载 ${info.versionName}")
                    setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    setDestinationInExternalFilesDir(
                        application,
                        Environment.DIRECTORY_DOWNLOADS,
                        fileName
                    )
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }

                val downloadId = dm.enqueue(request)
                Log.d(TAG, "下载任务已创建, ID=$downloadId")

                if (downloadId == -1L) {
                    _updateState.value = UpdateState.Error("无法创建下载任务")
                    return@launch
                }

                // 切到 IO 线程轮询
                withContext(Dispatchers.IO) {
                    pollDownload(dm, downloadId, fileName, info.sha256)
                }
            } catch (e: Exception) {
                Log.e(TAG, "下载异常", e)
                _updateState.value = UpdateState.Error("下载失败: ${e.message}")
            }
        }
    }

    /**
     * 轮询下载进度 (IO 线程)
     *
     * 用 query() 查状态，避免 getColumnIndex 返回 -1 的坑。
     */
    private suspend fun pollDownload(
        dm: DownloadManager,
        downloadId: Long,
        fileName: String,
        expectedSha256: String
    ) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val startTime = System.currentTimeMillis()
        var emptyPollCount = 0

        while (true) {
            delay(POLL_INTERVAL_MS)

            // 超时
            if (System.currentTimeMillis() - startTime > DOWNLOAD_TIMEOUT_MS) {
                Log.w(TAG, "下载超时")
                dm.remove(downloadId)
                _updateState.value = UpdateState.Error("下载超时，请检查网络后重试")
                return
            }

            val cursor: Cursor? = try {
                dm.query(query)
            } catch (e: Exception) {
                Log.e(TAG, "查询下载状态异常", e)
                null
            }

            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                emptyPollCount++
                Log.d(TAG, "cursor 为空, 第 $emptyPollCount 次")
                if (emptyPollCount >= MAX_EMPTY_POLLS) {
                    // 连续 15 次查不到 → 下载可能已失败或被系统清理
                    _updateState.value = UpdateState.Error("下载任务异常，请重试")
                    dm.remove(downloadId)
                    return
                }
                continue
            }

            // 重置空计数
            emptyPollCount = 0

            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            cursor.close()

            Log.d(TAG, "status=$status, total=$totalBytes, downloaded=$downloadedBytes, reason=$reason")

            when (status) {
                DownloadManager.STATUS_PENDING -> {
                    _updateState.value = UpdateState.Downloading(0f)
                }

                DownloadManager.STATUS_RUNNING -> {
                    val progress = if (totalBytes > 0) {
                        downloadedBytes.toFloat() / totalBytes
                    } else 0f
                    _updateState.value = UpdateState.Downloading(progress)
                }

                DownloadManager.STATUS_PAUSED -> {
                    // 暂停也算正常，等恢复
                    val progress = if (totalBytes > 0) {
                        downloadedBytes.toFloat() / totalBytes
                    } else 0f
                    _updateState.value = UpdateState.Downloading(progress)
                }

                DownloadManager.STATUS_SUCCESSFUL -> {
                    Log.d(TAG, "下载完成")
                    val file = downloadFile(fileName)
                    val verification = ApkVerifier.verify(application, file, expectedSha256)
                    if (verification.isFailure) {
                        file.delete()
                        _updateState.value = UpdateState.Error(
                            verification.exceptionOrNull()?.message ?: "APK 安全校验失败"
                        )
                        return
                    }
                    _updateState.value = UpdateState.Downloaded(downloadId)
                    installApk(file)
                    return
                }

                DownloadManager.STATUS_FAILED -> {
                    Log.e(TAG, "下载失败, reason=$reason")
                    _updateState.value = UpdateState.Error("下载失败 (错误码: $reason)")
                    return
                }
            }
        }
    }

    /** 安装 APK */
    private fun installApk(file: File) {
        try {
            if (!file.exists()) {
                Log.e(TAG, "APK 文件不存在: ${file.absolutePath}")
                _updateState.value = UpdateState.Error("APK 文件不存在，请重新下载")
                return
            }

            Log.d(TAG, "安装 APK: ${file.absolutePath}, 大小=${file.length()}")

            val apkUri = FileProvider.getUriForFile(
                application,
                "${application.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            application.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "安装失败", e)
            _updateState.value = UpdateState.Error("无法启动安装: ${e.message}")
        }
    }

    fun dismissUpdate() {
        val versionCode = _updateInfo.value?.versionCode ?: -1
        dismissedVersionCode = versionCode
        viewModelScope.launch {
            updatePrefs.setDismissedVersionCode(versionCode)
        }
        _updateState.value = UpdateState.Dismissed
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = application.packageManager.getPackageInfo(
                application.packageName, 0
            )
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            0
        }
    }

    private fun getCurrentVersionName(): String {
        return try {
            application.packageManager.getPackageInfo(application.packageName, 0).versionName.orEmpty()
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
    }

    private fun downloadFile(fileName: String): File {
        val directory = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(application.filesDir, "updates").apply { mkdirs() }
        return File(directory, fileName)
    }
}

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data class Downloaded(val downloadId: Long) : UpdateState()
    data class Error(val message: String) : UpdateState()
    data object Dismissed : UpdateState()
}
