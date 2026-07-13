package com.xiaomi.mimoclaw.data.chat

import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * MiMo 对话 API
 *
 * 基于 aistudio.xiaomimimo.com 的 API 分析。
 * 对话接口使用 SSE (Server-Sent Events) 流式输出。
 */
interface ChatApi {

    /**
     * 流式对话 - SSE
     * 返回 text/event-stream 格式
     */
    @POST("open-apis/chat/completions")
    @Streaming
    suspend fun chatCompletionsStream(
        @Body request: ChatRequest
    ): Response<ResponseBody>

    /**
     * 非流式对话
     */
    @POST("open-apis/chat/completions")
    suspend fun chatCompletions(
        @Body request: ChatRequest
    ): Response<ChatResponse>

    /**
     * 获取对话列表
     */
    @GET("open-apis/conversations")
    suspend fun getConversations(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): Response<ConversationListResponse>

    /**
     * 获取对话详情（含历史消息）
     */
    @GET("open-apis/conversations/{conversationId}")
    suspend fun getConversationDetail(
        @Path("conversationId") conversationId: String
    ): Response<ConversationDetailResponse>

    /**
     * 删除对话
     */
    @DELETE("open-apis/conversations/{conversationId}")
    suspend fun deleteConversation(
        @Path("conversationId") conversationId: String
    ): Response<Unit>

    companion object {
        const val BASE_URL = "https://aistudio.xiaomimimo.com/"
    }
}

// ── 对话列表响应 ──

data class ConversationListResponse(
    val code: Int = 0,
    val data: ConversationListData? = null
)

data class ConversationListData(
    val conversations: List<ConversationItem>? = null,
    val total: Int = 0
)

data class ConversationItem(
    val id: String? = null,
    val title: String? = null,
    @SerializedName("last_message") val lastMessage: String? = null,
    @SerializedName("created_at") val createdAt: Long = 0,
    @SerializedName("updated_at") val updatedAt: Long = 0
)

data class ConversationDetailResponse(
    val code: Int = 0,
    val data: ConversationDetailData? = null
)

data class ConversationDetailData(
    val id: String? = null,
    val title: String? = null,
    val messages: List<ApiMessage>? = null
)

data class ApiMessage(
    val id: String? = null,
    val role: String? = null,
    val content: String? = null,
    @SerializedName("created_at") val createdAt: Long = 0
)
