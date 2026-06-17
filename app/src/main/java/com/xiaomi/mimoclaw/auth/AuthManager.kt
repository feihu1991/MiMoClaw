package com.xiaomi.mimoclaw.auth

import com.xiaomi.mimoclaw.core.storage.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuthManager - 认证管理
 *
 * 使用小米账号 SSO 登录，登录态通过 Cookie 维持。
 * 与 Web 端 (aistudio.xiaomimimo.com) 保持一致。
 */
@Singleton
class AuthManager @Inject constructor(
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository
) {
    val isLoggedIn: Boolean
        get() = tokenManager.isLoggedIn()

    val username: String?
        get() = tokenManager.username

    /**
     * 通过 SSO Cookie 获取用户信息
     * 调用 /open-apis/user/mi/get 验证登录态
     */
    suspend fun fetchUserInfo(): Result<UserInfoData> = withContext(Dispatchers.IO) {
        try {
            val response = authRepository.getUserInfo()
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.data != null) {
                    val info = body.data
                    tokenManager.saveAuth(
                        accessToken = "sso_cookie",  // SSO 模式下 Token 存储在 Cookie 中
                        refreshToken = null,
                        expiresAt = System.currentTimeMillis() + (24 * 3600 * 1000L), // 24h
                        userId = info.userId,
                        username = info.nickname ?: info.userId
                    )
                    Result.success(info)
                } else {
                    Result.failure(Exception("用户信息为空"))
                }
            } else {
                when (response.code()) {
                    401 -> Result.failure(Exception("未登录或登录已过期"))
                    403 -> Result.failure(Exception("账号被锁定"))
                    else -> Result.failure(Exception("获取用户信息失败 (${response.code()})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("网络错误: ${e.message}"))
        }
    }

    /**
     * 验证当前登录态是否有效
     */
    suspend fun validateLogin(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = authRepository.getUserInfo()
            response.isSuccessful && response.body()?.data != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 登出
     */
    fun logout() {
        tokenManager.clear()
    }

    /**
     * 检查并验证登录态
     */
    suspend fun ensureValidToken(): Boolean {
        if (!isLoggedIn) return false
        return validateLogin()
    }
}


