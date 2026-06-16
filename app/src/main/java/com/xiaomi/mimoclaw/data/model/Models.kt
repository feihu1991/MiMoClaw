package com.xiaomi.mimoclaw.data.model

import java.util.UUID

// ──────────────────────────────────────────────
// Chat & Conversation
// ──────────────────────────────────────────────

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New conversation",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val mode: ChatMode = ChatMode.MIMO_CLAW
)

enum class ChatMode(val displayName: String, val tagline: String) {
    MIMO_CLAW("MiMo Claw", "Your AI Agent"),
    MIMO_CHAT("MiMo Chat", "New conversation")
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val attachments: List<Attachment> = emptyList()
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }

data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val type: AttachmentType,
    val name: String,
    val url: String? = null,
    val localUri: String? = null,
    val mimeType: String? = null
)

enum class AttachmentType { IMAGE, FILE, AUDIO, VIDEO }

// ──────────────────────────────────────────────
// API Request / Response
// ──────────────────────────────────────────────

data class ChatRequest(
    val model: String = "MiMo-V2.5-Pro",
    val messages: List<ApiMessage>,
    val stream: Boolean = true,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096
)

data class ApiMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val id: String?,
    val choices: List<Choice>?,
    val usage: Usage?
)

data class Choice(
    val index: Int,
    val message: ApiMessage?,
    val delta: Delta?,
    val finishReason: String?
)

data class Delta(
    val role: String?,
    val content: String?
)

data class Usage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

// ──────────────────────────────────────────────
// User & Auth
// ──────────────────────────────────────────────

data class User(
    val userId: String,
    val nickname: String,
    val avatar: String?,
    val email: String?,
    val phone: String?,
    val subscription: Subscription? = null
)

data class Subscription(
    val plan: PlanType,
    val startDate: Long,
    val endDate: Long,
    val tokenQuota: Long,
    val tokenUsed: Long
)

enum class PlanType(val displayName: String, val price: String) {
    FREE("Free", "¥0"),
    BASIC("Basic", "¥29/month"),
    PRO("Pro", "¥99/month"),
    ENTERPRISE("Enterprise", "Contact us")
}

data class LoginRequest(
    val account: String,
    val password: String
)

data class LoginResponse(
    val token: String?,
    val user: User?,
    val error: String?
)

// ──────────────────────────────────────────────
// App State
// ──────────────────────────────────────────────

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontSize: FontSize = FontSize.MEDIUM,
    val language: String = "zh-CN",
    val enableNotification: Boolean = true,
    val enableVoice: Boolean = false,
    val selectedModel: String = "MiMo-V2.5-Pro"
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }
enum class FontSize(val scale: Float, val label: String) {
    SMALL(0.85f, "Small"),
    MEDIUM(1.0f, "Medium"),
    LARGE(1.15f, "Large"),
    XLARGE(1.3f, "Extra Large")
}

data class FeatureCard(
    val title: String,
    val description: String,
    val icon: String // emoji or icon name
)
