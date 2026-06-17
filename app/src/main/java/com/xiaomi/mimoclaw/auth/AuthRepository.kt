package com.xiaomi.mimoclaw.auth

import retrofit2.Response
import retrofit2.http.*

interface AuthRepository {

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshRequest
    ): Response<LoginResponse>

    companion object {
        const val BASE_URL = "https://token-plan-cn.xiaomimimo.com/v1/"
    }
}

data class LoginRequest(
    val username: String,
    val password: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long,
    val userId: String?,
    val tokenType: String?
)
