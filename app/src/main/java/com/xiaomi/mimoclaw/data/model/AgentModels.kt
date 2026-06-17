package com.xiaomi.mimoclaw.data.model

import java.util.UUID

// ══════════════════════════════════════════════
// Task & Step - 核心任务模型
// ══════════════════════════════════════════════

data class AgentTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val steps: List<TaskStep> = emptyList(),
    val state: TaskState = TaskState.IDLE,
    val currentStepIndex: Int = -1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val logs: List<AgentLog> = emptyList(),
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val error: String? = null
)

data class TaskStep(
    val id: String = UUID.randomUUID().toString(),
    val type: StepType,
    val selector: String? = null,
    val value: String? = null,
    val description: String = "",
    val state: StepState = StepState.PENDING,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val error: String? = null,
    val screenshot: String? = null, // base64
    val result: String? = null
)

enum class StepType {
    OPEN,           // 打开 URL
    CLICK,          // 点击元素
    INPUT,          // 输入文本
    EXTRACT,        // 提取文本
    WAIT,           // 等待元素
    SCREENSHOT,     // 截图
    SCROLL,         // 滚动
    NAVIGATE_BACK,  // 返回
    LLM_DECISION,   // LLM 决策点
    VISION_CLICK,   // 视觉点击（DOM 不可用时）
    VISION_INPUT,   // 视觉输入
    CONDITIONAL,    // 条件分支
    WAIT_MS         // 等待毫秒
}

enum class TaskState {
    IDLE,       // 未开始
    RUNNING,    // 执行中
    PAUSED,     // 已暂停
    FAILED,     // 失败
    SUCCESS,    // 成功
    CANCELLED   // 已取消
}

enum class StepState {
    PENDING,    // 待执行
    RUNNING,    // 执行中
    SUCCESS,    // 成功
    FAILED,     // 失败
    SKIPPED,    // 跳过
    RETRYING    // 重试中
}

// ══════════════════════════════════════════════
// LLM Planner 模型
// ══════════════════════════════════════════════

data class PlanRequest(
    val instruction: String,
    val currentUrl: String? = null,
    val pageContent: String? = null,
    val previousError: String? = null,
    val failedStep: TaskStep? = null
)

data class PlanResponse(
    val name: String,
    val description: String = "",
    val steps: List<PlanStep>
)

data class PlanStep(
    val type: String,
    val selector: String? = null,
    val value: String? = null,
    val description: String? = null
)

// ══════════════════════════════════════════════
// Vision Agent 模型
// ══════════════════════════════════════════════

data class VisionRequest(
    val screenshot: String, // base64
    val instruction: String,
    val currentUrl: String? = null
)

data class VisionResponse(
    val action: String, // click, input, scroll
    val x: Float,
    val y: Float,
    val text: String? = null,
    val confidence: Float = 0f,
    val description: String = ""
)

// ══════════════════════════════════════════════
// WebController 操作结果
// ══════════════════════════════════════════════

data class ActionResult(
    val success: Boolean,
    val message: String = "",
    val data: String? = null,
    val screenshot: String? = null,
    val pageSource: String? = null
)

// ══════════════════════════════════════════════
// 结构化日志
// ══════════════════════════════════════════════

data class AgentLog(
    val stepId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val status: LogStatus,
    val message: String,
    val data: String? = null
)

enum class LogLevel { INFO, WARN, ERROR, DEBUG }
enum class LogStatus { STARTED, RUNNING, SUCCESS, FAILED, RETRYING, SKIPPED }

// ══════════════════════════════════════════════
// API 模型
// ══════════════════════════════════════════════

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
