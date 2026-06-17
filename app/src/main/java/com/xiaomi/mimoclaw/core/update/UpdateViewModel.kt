package com.xiaomi.mimoclaw.core.update

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
    private val application: Application
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    /** 已忽略的版本 (用户选择"稍后") */
    private var dismissedVersionCode: Int = -1

    init {
        checkUpdate()
    }

    /** 检查更新 */
    fun checkUpdate() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking

            try {
                val currentVersionCode = getCurrentVersionCode()
                val info = updateChecker.checkForUpdate(currentVersionCode)

                if (info != null && info.versionCode != dismissedVersionCode) {
                    _updateInfo.value = info
                    _updateState.value = UpdateState.Available(info)
                } else {
                    _updateState.value = UpdateState.UpToDate
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "检查更新失败")
            }
        }
    }

    /** 开始下载更新 */
    fun startDownload() {
        val info = _updateInfo.value ?: return
        if (info.downloadUrl.isBlank()) {
            _updateState.value = UpdateState.Error("下载链接不可用")
            return
        }

        viewModelScope.launch {
            try {
                _updateState.value = UpdateState.Downloading(0f)

                val downloadManager =
                    application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

                val request = DownloadManager.Request(Uri.parse(info.downloadUrl)).apply {
                    setTitle("MiMo Agent 更新")
                    setDescription("正在下载 v${info.versionName}")
                    setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "mimo-agent-update.apk"
                    )
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }

                val downloadId = downloadManager.enqueue(request)

                // 轮询下载进度
                pollDownloadProgress(downloadManager, downloadId)
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("下载失败: ${e.message}")
            }
        }
    }

    /** 轮询下载进度 */
    private suspend fun pollDownloadProgress(
        downloadManager: DownloadManager,
        downloadId: Long
    ) {
        val query = DownloadManager.Query().setFilterById(downloadId)

        while (true) {
            kotlinx.coroutines.delay(500)

            val cursor = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val statusIdx =
                    cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val totalIdx =
                    cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val downloadedIdx =
                    cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)

                val status = cursor.getInt(statusIdx)
                val total = cursor.getLong(totalIdx)
                val downloaded = cursor.getLong(downloadedIdx)
                cursor.close()

                when (status) {
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                        val progress = if (total > 0) downloaded.toFloat() / total else 0f
                        _updateState.value = UpdateState.Downloading(progress)
                    }

                    DownloadManager.STATUS_SUCCESSFUL -> {
                        _updateState.value = UpdateState.Downloaded(downloadId)
                        installApk(downloadId, downloadManager)
                        return
                    }

                    DownloadManager.STATUS_FAILED -> {
                        _updateState.value = UpdateState.Error("下载失败")
                        return
                    }
                }
            } else {
                cursor?.close()
            }
        }
    }

    /** 安装 APK */
    private fun installApk(downloadId: Long, downloadManager: DownloadManager) {
        val uri = downloadManager.getUriForDownloadedFile(downloadId) ?: return

        // 对于 content:// URI，需要使用 FileProvider 或直接读取
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "mimo-agent-update.apk"
        )

        val apkUri = if (file.exists()) {
            FileProvider.getUriForFile(
                application,
                "${application.packageName}.fileprovider",
                file
            )
        } else {
            uri
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            application.startActivity(intent)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("无法启动安装: ${e.message}")
        }
    }

    /** 用户选择稍后更新 */
    fun dismissUpdate() {
        dismissedVersionCode = _updateInfo.value?.versionCode ?: -1
        _updateState.value = UpdateState.Dismissed
    }

    /** 获取当前版本号 */
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
}

/** 更新状态 */
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
