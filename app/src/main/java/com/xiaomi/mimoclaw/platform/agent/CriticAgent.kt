package com.xiaomi.mimoclaw.platform.agent

import com.xiaomi.mimoclaw.agent.classifier.FailureClassifier
import com.xiaomi.mimoclaw.agent.healing.HealingResult
import com.xiaomi.mimoclaw.agent.healing.SelfHealingSystem
import com.xiaomi.mimoclaw.data.model.*
import com.xiaomi.mimoclaw.platform.event.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CriticAgent - 错误检测 + 修复Agent
 * 职责: 评估执行结果，决定修复策略
 * 接收: WorkerAgent的执行结果
 * 输出: 修复建议 → 发送给WorkerAgent / VisionAgent
 */
@Singleton
class CriticAgent @Inject constructor(
    private val failureClassifier: FailureClassifier,
    private val selfHealingSystem: SelfHealingSystem,
    private val eventBus: AgentEventBus
) {
    val agentId = "critic-001"
    
    /**
     * 评估执行结果并决定下一步
     */
    suspend fun evaluate(
        step: TaskStep,
        workerResult: WorkerResult,
        task: AgentTask,
        taskId: String
    ): CriticDecision {
        val observation = workerResult.observation
        
        if (workerResult.success) {
            return CriticDecision(
                action = CriticAction.APPROVE,
                reason = "步骤执行成功"
            )
        }
        
        // 分类错误
        val classification = failureClassifier.classify(
            step, workerResult.actionResult, observation, null
        )
        
        // 发布评估结果
        eventBus.publish(
            agentId = agentId,
            agentType = AgentType.CRITIC,
            type = MessageType.REPAIR_SUGGESTED,
            taskId = taskId,
            payload = MessagePayload(
                stepIndex = task.steps.indexOf(step),
                error = classification.message,
                metadata = mapOf(
                    "failureType" to classification.type.name,
                    "shouldRetry" to classification.shouldRetry.toString(),
                    "shouldUseVision" to classification.shouldUseVision.toString(),
                    "shouldUseLLMRepair" to classification.shouldUseLLMRepair.toString()
                )
            )
        )
        
        return when {
            // DOM未找到 → 请求Vision
            classification.shouldUseVision -> {
                eventBus.publish(
                    agentId = agentId,
                    agentType = AgentType.CRITIC,
                    type = MessageType.VISION_REQUESTED,
                    taskId = taskId,
                    payload = MessagePayload(
                        stepIndex = task.steps.indexOf(step),
                        instruction = "找到并${step.type.name.lowercase()}: ${step.description}",
                        screenshot = observation.screenshot,
                        currentUrl = observation.currentUrl
                    )
                )
                CriticDecision(
                    action = CriticAction.VISION_FALLBACK,
                    reason = "DOM未找到，使用Vision兜底",
                    classification = classification
                )
            }
            
            // 可重试 → 批准重试
            classification.shouldRetry && step.retryCount < step.maxRetries -> {
                eventBus.publish(
                    agentId = agentId,
                    agentType = AgentType.CRITIC,
                    type = MessageType.RETRY_APPROVED,
                    taskId = taskId,
                    payload = MessagePayload(
                        stepIndex = task.steps.indexOf(step),
                        metadata = mapOf("waitMs" to classification.waitBeforeRetryMs.toString())
                    )
                )
                CriticDecision(
                    action = CriticAction.RETRY,
                    reason = "错误可重试: ${classification.type.name}",
                    classification = classification,
                    waitMs = classification.waitBeforeRetryMs
                )
            }
            
            // 需要LLM修复
            classification.shouldUseLLMRepair -> {
                CriticDecision(
                    action = CriticAction.LLM_REPAIR,
                    reason = "需要LLM修复: ${classification.type.name}",
                    classification = classification
                )
            }
            
            // 超过重试次数 → 跳过或失败
            step.retryCount >= step.maxRetries -> {
                eventBus.publish(
                    agentId = agentId,
                    agentType = AgentType.CRITIC,
                    type = MessageType.SKIP_APPROVED,
                    taskId = taskId,
                    payload = MessagePayload(
                        stepIndex = task.steps.indexOf(step),
                        error = "超过最大重试次数"
                    )
                )
                CriticDecision(
                    action = CriticAction.SKIP,
                    reason = "超过最大重试次数",
                    classification = classification
                )
            }
            
            else -> {
                CriticDecision(
                    action = CriticAction.FAIL,
                    reason = "无法修复: ${classification.message}",
                    classification = classification
                )
            }
        }
    }
    
    /**
     * 调用LLM修复
     */
    fun repair(
        task: AgentTask,
        failedStep: TaskStep,
        observation: Observation,
        classification: FailureClassification,
        taskId: String
    ): Flow<HealingResult> = flow {
        val request = RepairRequest(
            originalTask = task,
            failedStep = failedStep,
            observation = observation,
            failureClassification = classification,
            checkpoint = TaskCheckpoint(taskId = task.id, currentStep = task.currentStepIndex)
        )
        
        selfHealingSystem.repair(request).collect { result ->
            when (result) {
                is HealingResult.Analyzing -> {
                    eventBus.publish(
                        agentId = agentId,
                        agentType = AgentType.CRITIC,
                        type = MessageType.LOG,
                        taskId = taskId,
                        payload = MessagePayload(instruction = "LLM修复分析中...")
                    )
                }
                is HealingResult.Repaired -> {
                    eventBus.publish(
                        agentId = agentId,
                        agentType = AgentType.CRITIC,
                        type = MessageType.REPAIR_SUGGESTED,
                        taskId = taskId,
                        payload = MessagePayload(
                            stepIndex = task.steps.indexOf(failedStep),
                            metadata = mapOf("explanation" to result.response.explanation)
                        )
                    )
                }
                is HealingResult.Failed -> {}
            }
            emit(result)
        }
    }
}

data class CriticDecision(
    val action: CriticAction,
    val reason: String,
    val classification: FailureClassification? = null,
    val waitMs: Long = 0
)

enum class CriticAction {
    APPROVE,        // 批准通过
    RETRY,          // 批准重试
    VISION_FALLBACK, // 使用Vision兜底
    LLM_REPAIR,     // 使用LLM修复
    SKIP,           // 跳过步骤
    FAIL            // 标记失败
}
