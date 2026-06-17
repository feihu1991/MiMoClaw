package com.xiaomi.mimoclaw.data.model

import android.graphics.Bitmap
import java.util.UUID

// ══════════════════════════════════════════════
// Observation System - 执行结果感知
// ══════════════════════════════════════════════

data class Observation(
    val stepId: String,
    val success: Boolean,
    val pageState: PageState,
    val error: String? = null,
    val screenshot: String? = null, // base64
    val pageSource: String? = null,
    val currentUrl: String = "",
    val pageTitle: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val domExists: Boolean = true,
    val extractedText: String? = null
)

data class PageState(
    val url: String,
    val title: String,
    val readyState: String = "complete",
    val hasLoginForm: Boolean = false,
    val hasSearchForm: Boolean = false,
    val elementCount: Int = 0,
    val visibleText: String = ""
)

// ══════════════════════════════════════════════
// Failure Classification
// ══════════════════════════════════════════════

enum class FailureType {
    DOM_NOT_FOUND,      // 元素不存在 → Vision fallback
    JS_ERROR,           // JS执行失败 → retry
    TIMEOUT,            // 超时 → wait + retry
    PAGE_CHANGE,        // 页面变化 → LLM repair
    NAVIGATION_ERROR,   // 导航失败 → retry
    NETWORK_ERROR,      // 网络错误 → retry
    ELEMENT_NOT_CLICKABLE, // 元素不可点击 → wait + retry
    UNKNOWN             // 未知 → LLM repair
}

data class FailureClassification(
    val type: FailureType,
    val message: String,
    val shouldRetry: Boolean,
    val shouldUseVision: Boolean,
    val shouldUseLLMRepair: Boolean,
    val waitBeforeRetryMs: Long = 0,
    val confidence: Float = 1.0f
)

// ══════════════════════════════════════════════
// Task Checkpoint - 支持恢复执行
// ══════════════════════════════════════════════

data class TaskCheckpoint(
    val taskId: String,
    val currentStep: Int,
    val completedSteps: List<String> = emptyList(), // step IDs
    val failedSteps: List<String> = emptyList(),
    val stepResults: Map<String, String> = emptyMap(), // stepId → result
    val observations: List<Observation> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isStepCompleted(stepId: String): Boolean = stepId in completedSteps
    fun isStepFailed(stepId: String): Boolean = stepId in failedSteps
}

// ══════════════════════════════════════════════
// Agent Loop 状态
// ══════════════════════════════════════════════

enum class LoopPhase {
    IDLE,
    OBSERVING,
    DECIDING,
    ACTING,
    VERIFYING,
    REPAIRING,
    COMPLETED,
    FAILED
}

data class LoopState(
    val phase: LoopPhase = LoopPhase.IDLE,
    val currentStepIndex: Int = -1,
    val observation: Observation? = null,
    val failureClassification: FailureClassification? = null,
    val repairAttempts: Int = 0,
    val maxRepairAttempts: Int = 3,
    val visionFallbackUsed: Boolean = false,
    val llmRepairUsed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

// ══════════════════════════════════════════════
// Self-Healing 修复请求/响应
// ══════════════════════════════════════════════

data class RepairRequest(
    val originalTask: AgentTask,
    val failedStep: TaskStep,
    val observation: Observation,
    val failureClassification: FailureClassification,
    val checkpoint: TaskCheckpoint
)

data class RepairResponse(
    val repairedStep: TaskStep?,
    val shouldSkip: Boolean = false,
    val shouldRetry: Boolean = false,
    val alternativeSelector: String? = null,
    val alternativeAction: StepType? = null,
    val explanation: String = ""
)
