package com.xiaomi.mimoclaw.auth

import android.webkit.CookieManager
import com.xiaomi.mimoclaw.core.network.ClawGateway
import com.xiaomi.mimoclaw.core.storage.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository,
    private val gateway: ClawGateway
) {
    suspend fun fetchUserInfo(): Result<UserInfoData> = withContext(Dispatchers.IO) {
        runCatching {
            val response = authRepository.getUserInfo()
            if (!response.isSuccessful) {
                val message = when (response.code()) {
                    401 -> "未登录或登录已过期"
                    403 -> "账号被锁定"
                    else -> "获取用户信息失败 (${response.code()})"
                }
                error(message)
            }

            val info = response.body()?.data ?: error("用户信息为空")
            tokenManager.saveAuth(
                accessToken = "sso_cookie",
                refreshToken = null,
                expiresAt = System.currentTimeMillis() + AUTH_CACHE_DURATION_MS,
                userId = info.userId,
                username = info.nickname ?: info.userId
            )
            info
        }
    }

    suspend fun validateLogin(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val response = authRepository.getUserInfo()
            response.isSuccessful && response.body()?.data != null
        }.getOrDefault(false)
    }

    fun logout() {
        gateway.disconnect()
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
        }
        tokenManager.clear()
    }

    private companion object {
        const val AUTH_CACHE_DURATION_MS = 24 * 60 * 60 * 1000L
    }
}
