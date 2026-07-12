package com.xiaomi.mimoclaw.data.chat

import com.google.gson.annotations.SerializedName

// ── 请求模型 ──

data class ChatRequest(
    @SerializedName("messages") val messages: List<ChatMessage>,
    @SerializedName("stream") val stream: Boolean = true,
    @SerializedName("model") val model: String? = null
)

data class ChatMessage(
    @SerializedName("role") val role: String,  // "user" | "assistant" | "system"
    @SerializedName("content") val content: String
)

// ── 响应模型 ──

/** SSE 流式响应的单个 chunk */
data class ChatStreamChunk(
    @SerializedName("id") val id: String? = null,
    @SerializedName("object") val objectType: String? = null,
    @SerializedName("choices") val choices: List<StreamChoice>? = null
)

data class StreamChoice(
    @SerializedName("index") val index: Int = 0,
    @SerializedName("delta") val delta: StreamDelta? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class StreamDelta(
    @SerializedName("role") val role: String? = null,
    @SerializedName("content") val content: String? = null
)

/** 非流式响应 */
data class ChatResponse(
    @SerializedName("code") val code: Int = 0,
    @SerializedName("data") val data: ChatResponseData? = null
)

data class ChatResponseData(
    @SerializedName("id") val id: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("conversationId") val conversationId: String? = null
)

// ── 本地对话模型 ──

data class Conversation(
    val id: String,
    val title: String,
    val messages: List<UiMessage>,
    val createdAt: Long,
    val updatedAt: Long
)

data class UiMessage(
    val id: String,
    val role: String,  // "user" | "assistant" | "system"
    val content: String,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
