package com.xiaomi.mimoclaw.platform.agent

import com.xiaomi.mimoclaw.agent.observation.ObservationSystem
import com.xiaomi.mimoclaw.agent.webcontroller.WebController
import com.xiaomi.mimoclaw.data.model.*
import com.xiaomi.mimoclaw.platform.event.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkerAgent - 任务执行Agent
 * 职责: 执行具体的Web操作步骤
 * 接收: Step分配 → 执行 → 返回Observation
 * 输出: 执行结果 → 发送给CriticAgent
 */
@Singleton
class WorkerAgent @Inject constructor(
    private val webController: WebController,
    private val observationSystem: ObservationSystem,
    private val eventBus: AgentEventBus
) {
    val agentId = "worker-001"
    
    /**
     * 执行单个步骤
     */
    suspend fun executeStep(
        step: TaskStep,
        stepIndex: Int,
        taskId: String
    ): WorkerResult = withContext(Dispatchers.IO) {
        // 发布执行开始
        eventBus.publish(
            agentId = agentId,
            agentType = AgentType.WORKER,
            type = MessageType.STEP_ASSIGNED,
            taskId = taskId,
            payload = MessagePayload(
                stepIndex = stepIndex,
                stepType = step.type.name,
                selector = step.selector,
                value = step.value
            )
        )
        
        try {
            // 执行步骤
            val actionResult = performAction(step)
            
            // 收集Observation
            val observation = observationSystem.observe(step.id, actionResult, step)
            
            if (actionResult.success) {
                // 发布成功
                eventBus.publish(
                    agentId = agentId,
                    agentType = AgentType.WORKER,
                    type = MessageType.STEP_COMPLETED,
                    taskId = taskId,
                    payload = MessagePayload(
                        stepIndex = stepIndex,
                        currentUrl = observation.currentUrl
                    ),
                    result = MessageResult(
                        success = true,
                        data = actionResult.data
                    )
                )
                
                WorkerResult(
                    success = true,
                    observation = observation,
                    actionResult = actionResult
                )
            } else {
                // 发布失败
                eventBus.publish(
                    agentId = agentId,
                    agentType = AgentType.WORKER,
                    type = MessageType.STEP_FAILED,
                    taskId = taskId,
                    payload = MessagePayload(
                        stepIndex = stepIndex,
                        error = actionResult.message,
                        selector = step.selector,
                        currentUrl = observation.currentUrl,
                        pageSource = observation.pageSource?.take(2000)
                    ),
                    result = MessageResult(
                        success = false,
                        error = actionResult.message
                    )
                )
                
                WorkerResult(
                    success = false,
                    observation = observation,
                    actionResult = actionResult,
                    error = actionResult.message
                )
            }
        } catch (e: Exception) {
            val observation = observationSystem.quickObserve(step.id, false, e.message)
            
            eventBus.publish(
                agentId = agentId,
                agentType = AgentType.WORKER,
                type = MessageType.STEP_FAILED,
                taskId = taskId,
                payload = MessagePayload(
                    stepIndex = stepIndex,
                    error = e.message
                ),
                result = MessageResult(success = false, error = e.message)
            )
            
            WorkerResult(
                success = false,
                observation = observation,
                actionResult = ActionResult(false, e.message ?: "Unknown error"),
                error = e.message
            )
        }
    }
    
    /**
     * Vision模式执行（坐标点击/输入）
     */
    suspend fun executeVisionAction(
        action: String,
        x: Float,
        y: Float,
        text: String?,
        taskId: String
    ): ActionResult = withContext(Dispatchers.IO) {
        when (action) {
            "click" -> webController.clickAtCoordinate(x, y)
            "input" -> webController.inputAtCoordinate(x, y, text ?: "")
            else -> ActionResult(false, "Unknown vision action: $action")
        }
    }
    
    private suspend fun performAction(step: TaskStep): ActionResult {
        return when (step.type) {
            StepType.OPEN -> webController.open(step.value ?: "")
            StepType.CLICK -> webController.click(step.selector ?: "")
            StepType.INPUT -> webController.input(step.selector ?: "", step.value ?: "")
            StepType.EXTRACT -> webController.extract(step.selector ?: "")
            StepType.WAIT -> webController.waitForElement(step.selector ?: "")
            StepType.SCREENSHOT -> webController.screenshot()
            StepType.SCROLL -> webController.scroll(step.value ?: "down")
            StepType.NAVIGATE_BACK -> webController.navigateBack()
            StepType.WAIT_MS -> {
                delay(step.value?.toLongOrNull() ?: 1000)
                ActionResult(true, "等待完成")
            }
            StepType.VISION_CLICK -> ActionResult(false, "需要Vision处理")
            StepType.VISION_INPUT -> ActionResult(false, "需要Vision处理")
            StepType.LLM_DECISION -> ActionResult(true, "LLM决策点")
            StepType.CONDITIONAL -> ActionResult(true, "条件分支")
        }
    }
}

data class WorkerResult(
    val success: Boolean,
    val observation: Observation,
    val actionResult: ActionResult,
    val error: String? = null
)
