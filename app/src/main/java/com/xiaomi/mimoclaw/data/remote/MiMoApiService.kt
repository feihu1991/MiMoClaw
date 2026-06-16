package com.xiaomi.mimoclaw.data.remote

import com.xiaomi.mimoclaw.data.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface MiMoApiService {

    companion object {
        const val BASE_URL = "https://aistudio.xiaomimimo.com"
        const val API_BASE = "https://token-plan-cn.xiaomimimo.com/v1/"
    }

    // ── Auth ──
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("user/profile")
    suspend fun getProfile(@Header("Authorization") token: String): Response<User>

    // ── Chat Completions (OpenAI-compatible) ──
    @POST("chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") token: String,
        @Body request: ChatRequest
    ): Response<ChatResponse>

    @Streaming
    @POST("chat/completions")
    suspend fun chatCompletionsStream(
        @Header("Authorization") token: String,
        @Body request: ChatRequest
    ): Response<ResponseBody>

    // ── Models ──
    @GET("models")
    suspend fun getModels(@Header("Authorization") token: String): Response<ModelsResponse>

    // ── Conversations ──
    @GET("conversations")
    suspend fun getConversations(
        @Header("Authorization") token: String,
        @Query("mode") mode: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 50
    ): Response<ConversationsResponse>

    @GET("conversations/{id}")
    suspend fun getConversationDetail(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String
    ): Response<ConversationDetailResponse>

    @DELETE("conversations/{id}")
    suspend fun deleteConversation(
        @Header("Authorization") token: String,
        @Path("id") conversationId: String
    ): Response<Unit>
}

// ── API Response Wrappers ──

data class ModelsResponse(
    val data: List<ModelInfo>?
)

data class ModelInfo(
    val id: String,
    val name: String?,
    val description: String?
)

data class ConversationsResponse(
    val data: List<ConversationApi>?,
    val total: Int?,
    val page: Int?,
    val pageSize: Int?
)

data class ConversationApi(
    val id: String,
    val title: String?,
    val mode: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class ConversationDetailResponse(
    val id: String,
    val title: String?,
    val messages: List<ApiMessage>?
)
