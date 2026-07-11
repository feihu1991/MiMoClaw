package com.xiaomi.mimoclaw.feature.chat.model

import java.util.UUID

/**
 * 聊天消息
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    /** AI 思考过程 (仅 ASSISTANT 角色) */
    val thinkingContent: String = "",
    /** 是否正在流式输出 */
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Role {
        USER,
        ASSISTANT,
        SYSTEM
    }
}

/**
 * 对话(一组消息)
 */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val model: String = "mimo-v2.5-pro",
    /** 服务端 session key */
    val sessionKey: String? = null
)
