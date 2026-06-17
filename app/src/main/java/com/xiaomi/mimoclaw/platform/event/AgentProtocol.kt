package com.xiaomi.mimoclaw.platform.event

import java.util.UUID

// ══════════════════════════════════════════════
// Agent Communication Protocol - 统一通信协议
// 所有Agent之间通过此协议通信
// ══════════════════════════════════════════════

data class AgentMessage(
    val id: String = UUID.randomUUID().toString(),
    val agentId: String,
    val agentType: AgentType,
    val type: MessageType,
    val taskId: String,
    val payload: MessagePayload,
    val result: MessageResult? = null,
    val status: MessageStatus = MessageStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis(),
    val replyTo: String? = null, // 关联消息ID
    val priority: Int = 0 // 0=普通, 1=高, 2=紧急
)

enum class AgentType {
    PLANNER,    // 任务规划Agent
    WORKER,     // 执行Agent
    CRITIC,     // 错误检测+修复Agent
    VISION,     // 视觉识别Agent
    ORCHESTRATOR // 调度Agent（新增）
}

enum class MessageType {
    // Planner → Worker
    PLAN_CREATED,
    STEP_ASSIGNED,
    
    // Worker → Critic
    STEP_COMPLETED,
    STEP_FAILED,
    
    // Critic → Worker
    REPAIR_SUGGESTED,
    RETRY_APPROVED,
    SKIP_APPROVED,
    
    // Critic → Vision
    VISION_REQUESTED,
    
    // Vision → Critic
    VISION_RESULT,
    
    // Orchestrator → All
    TASK_PAUSED,
    TASK_RESUMED,
    TASK_CANCELLED,
    
    // System
    HEARTBEAT,
    ERROR,
    LOG
}

data class MessagePayload(
    val instruction: String? = null,
    val stepIndex: Int? = null,
    val stepType: String? = null,
    val selector: String? = null,
    val value: String? = null,
    val screenshot: String? = null, // base64
    val pageSource: String? = null,
    val currentUrl: String? = null,
    val error: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class MessageResult(
    val success: Boolean,
    val data: String? = null,
    val error: String? = null,
    val confidence: Float = 0f,
    val coordinates: Pair<Float, Float>? = null,
    val repairedStep: Map<String, String>? = null
)

enum class MessageStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    TIMEOUT
}
