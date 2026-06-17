package com.xiaomi.mimoclaw.auth

import com.xiaomi.mimoclaw.core.storage.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuthManager - 认证管理
 * 负责: login / logout / token refresh / auth state
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
     * 登录
     */
    suspend fun login(username: String, password: String): Result<AuthInfo> = withContext(Dispatchers.IO) {
        try {
            val response = authRepository.login(LoginRequest(username, password))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    tokenManager.saveAuth(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken,
                        expiresAt = System.currentTimeMillis() + (body.expiresIn * 1000L),
                        userId = body.userId,
                        username = username
                    )
                    Result.success(AuthInfo(
                        userId = body.userId ?: "",
                        username = username,
                        accessToken = body.accessToken
                    ))
                } else {
                    Result.failure(Exception("登录响应为空"))
                }
            } else {
                val errorMsg = when (response.code()) {
                    401 -> "账号或密码错误"
                    403 -> "账号被锁定"
                    429 -> "请求过于频繁，请稍后再试"
                    else -> "登录失败 (${response.code()})"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("网络错误: ${e.message}"))
        }
    }

    /**
     * 刷新Token
     */
    suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            val refreshToken = tokenManager.refreshToken ?: return@withContext false
            val response = authRepository.refreshToken(RefreshRequest(refreshToken))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    tokenManager.saveAuth(
                        accessToken = body.accessToken,
                        refreshToken = body.refreshToken ?: refreshToken,
                        expiresAt = System.currentTimeMillis() + (body.expiresIn * 1000L),
                        userId = tokenManager.userId,
                        username = tokenManager.username
                    )
                    return@withContext true
                }
            }
            false
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
     * 检查并刷新Token
     */
    suspend fun ensureValidToken(): Boolean {
        if (!isLoggedIn) return false
        if (tokenManager.isTokenExpired()) {
            return refreshToken()
        }
        return true
    }
}

data class AuthInfo(
    val userId: String,
    val username: String,
    val accessToken: String
)
