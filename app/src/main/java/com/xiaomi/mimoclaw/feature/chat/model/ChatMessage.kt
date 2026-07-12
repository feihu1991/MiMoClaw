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
    /** Agent 在本次回复中执行的工具步骤 */
    val toolCalls: List<ToolCall> = emptyList(),
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

data class ToolCall(
    val name: String,
    val status: String = "执行中"
)

/**
 * 显示项 — LazyColumn 的扁平化数据源
 *
 * 将 ChatMessage 拆分为用户气泡、助手气泡、工具调用组、思考块四种显示项，
 * 参考 happy 项目的 DisplayItem / useGroupedMessages 设计。
 */
sealed class DisplayItem {
    abstract val id: String

    /** 用户消息气泡（右对齐） */
    data class UserMessage(
        override val id: String,
        val message: ChatMessage
    ) : DisplayItem()

    /** 助手消息气泡（左对齐，带头像） */
    data class AssistantMessage(
        override val id: String,
        val message: ChatMessage
    ) : DisplayItem()

    /** 可折叠的工具调用组 */
    data class ToolGroup(
        override val id: String,
        val tools: List<ToolCall>,
        val isRunning: Boolean = false
    ) : DisplayItem()

    /** AI 思考过程块（可折叠） */
    data class ThinkingBlock(
        override val id: String,
        val text: String
    ) : DisplayItem()
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
