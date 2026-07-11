package com.xiaomi.mimoclaw.core.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object ApkVerifier {
    fun verify(
        context: Context,
        apkFile: File,
        expectedSha256: String
    ): Result<Unit> = runCatching {
        require(expectedSha256.matches(Regex("[0-9a-fA-F]{64}"))) {
            "发布资产缺少有效 SHA-256，已拒绝安装"
        }
        val actualSha256 = sha256(apkFile)
        require(actualSha256.equals(expectedSha256, ignoreCase = true)) {
            "APK 完整性校验失败"
        }

        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: error("无法解析下载的 APK")
        require(archive.packageName == context.packageName) {
            "APK 包名不匹配"
        }

        val installed = packageManager.getPackageInfo(context.packageName, flags)
        val installedSigners = signerDigests(installed)
        val archiveSigners = signerDigests(archive)
        require(installedSigners.isNotEmpty() && archiveSigners.isNotEmpty()) {
            "无法读取 APK 签名"
        }
        require(installedSigners.intersect(archiveSigners).isNotEmpty()) {
            "APK 签名与当前应用不一致"
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun signerDigests(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures.orEmpty()
        }
        return signatures.mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
