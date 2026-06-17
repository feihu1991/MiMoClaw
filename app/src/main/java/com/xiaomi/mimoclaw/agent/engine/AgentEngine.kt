package com.xiaomi.mimoclaw.agent.engine

import com.xiaomi.mimoclaw.agent.log.StructuredLogger
import com.xiaomi.mimoclaw.agent.planner.LLMPlanner
import com.xiaomi.mimoclaw.agent.planner.PlanResult
import com.xiaomi.mimoclaw.agent.state.TaskStateMachine
import com.xiaomi.mimoclaw.agent.vision.VisionAgent
import com.xiaomi.mimoclaw.agent.vision.VisionResult
import com.xiaomi.mimoclaw.agent.webcontroller.WebController
import com.xiaomi.mimoclaw.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AgentEngine - 核心执行引擎
 * 负责任务调度、步骤执行、失败修复、Vision兜底
 *
 * 执行闭环:
 * 用户输入 → LLM生成Task → 逐步执行 → 失败? → LLM修复/Vision接管 → 继续 → 完成
 */
@Singleton
class AgentEngine @Inject constructor(
    private val stateMachine: TaskStateMachine,
    private val webController: WebController,
    private val planner: LLMPlanner,
    private val visionAgent: VisionAgent,
    private val logger: StructuredLogger
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var executionJob: Job? = null

    val currentTask: StateFlow<AgentTask?> = stateMachine.currentTask
    val logs: StateFlow<List<AgentLog>> = stateMachine.logs

    // ── 任务生命周期 ──

    /**
     * 从自然语言创建并执行任务
     */
    fun executeFromInstruction(instruction: String) {
        engineScope.launch {
            logger.info("engine", "收到指令: $instruction")

            // 1. LLM 规划
            planner.plan(PlanRequest(instruction = instruction)).collect { result ->
                when (result) {
                    is PlanResult.Planning -> {
                        logger.info("engine", "LLM 正在规划任务...")
                    }
                    is PlanResult.Success -> {
                        logger.success("engine", "任务规划完成: ${result.task.name} (${result.task.steps.size} 步)")
                        executeTask(result.task)
                    }
                    is PlanResult.Error -> {
                        logger.error("engine", "规划失败: ${result.message}")
                        stateMachine.failTask("规划失败: ${result.message}")
                    }
                }
            }
        }
    }

    /**
     * 直接执行已有任务
     */
    fun executeTask(task: AgentTask) {
        executionJob?.cancel()
        executionJob = engineScope.launch {
            stateMachine.setTask(task)
            stateMachine.startTask()

            var currentIndex = 0
            while (currentIndex < task.steps.size && stateMachine.isRunning()) {
                val step = task.steps[currentIndex]

                // 检查暂停
                while (stateMachine.isPaused()) {
                    delay(500)
                }

                if (!stateMachine.isRunning()) break

                // 执行步骤
                val success = executeStep(currentIndex, step)

                if (success) {
                    stateMachine.completeStep(currentIndex)
                    currentIndex++
                } else {
                    // 失败处理
                    val canRetry = stateMachine.canRetryCurrentStep()
                    if (canRetry) {
                        stateMachine.retryStep(currentIndex)
                        continue // 重试当前步骤
                    }

                    // 尝试 Vision 兜底
                    val visionSuccess = tryVisionFallback(currentIndex, step)
                    if (visionSuccess) {
                        stateMachine.completeStep(currentIndex, "Vision 兜底成功")
                        currentIndex++
                    } else {
                        // 尝试 LLM 修复
                        val fixSuccess = tryLLMFix(task, step, currentIndex)
                        if (fixSuccess) {
                            continue // 用修复后的任务重新执行
                        }

                        stateMachine.failTask("步骤失败: ${step.description}")
                        break
                    }
                }
            }

            // 全部完成
            if (stateMachine.currentTask.value?.state == TaskState.RUNNING) {
                stateMachine.completeTask()
            }
        }
    }

    fun pause() {
        stateMachine.pauseTask()
    }

    fun resume() {
        stateMachine.resumeTask()
    }

    fun cancel() {
        executionJob?.cancel()
        stateMachine.cancelTask()
    }

    fun retry() {
        val task = stateMachine.currentTask.value ?: return
        executeTask(task.copy(retryCount = task.retryCount + 1))
    }

    // ── 步骤执行 ──

    private suspend fun executeStep(index: Int, step: TaskStep): Boolean {
        stateMachine.startStep(index)
        logger.info(step.id, "执行: ${step.type.name} - ${step.description}")

        return try {
            val result = when (step.type) {
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
                StepType.LLM_DECISION -> {
                    // LLM 决策点 - 暂停等待用户输入
                    ActionResult(true, "LLM 决策点")
                }
                StepType.VISION_CLICK -> {
                    ActionResult(false, "需要 Vision 处理")
                }
                StepType.VISION_INPUT -> {
                    ActionResult(false, "需要 Vision 处理")
                }
                StepType.CONDITIONAL -> {
                    ActionResult(true, "条件分支")
                }
            }

            if (result.success) {
                logger.success(step.id, result.message)
            } else {
                logger.error(step.id, result.message)
            }

            result.success
        } catch (e: Exception) {
            logger.error(step.id, "异常: ${e.message}")
            false
        }
    }

    // ── Vision 兜底 ──

    private suspend fun tryVisionFallback(index: Int, step: TaskStep): Boolean {
        logger.warn(step.id, "DOM 操作失败，尝试 Vision 兜底...")

        val screenshotResult = webController.screenshot()
        if (!screenshotResult.success) return false

        val instruction = when (step.type) {
            StepType.CLICK -> "点击: ${step.description}"
            StepType.INPUT -> "输入框: ${step.description}"
            else -> step.description
        }

        var success = false
        visionAgent.analyze(VisionRequest(
            screenshot = screenshotResult.data ?: "",
            instruction = instruction
        )).collect { result ->
            when (result) {
                is VisionResult.Analyzing -> {
                    logger.info(step.id, "Vision 分析中...")
                }
                is VisionResult.Success -> {
                    val resp = result.response
                    logger.info(step.id, "Vision 结果: ${resp.description} (置信度: ${resp.confidence})")

                    if (resp.confidence > 0.3f) {
                        val w = webController.getCurrentUrl().let { 1080f } // 获取实际宽度
                        val h = 1920f
                        val pixelX = resp.x * w
                        val pixelY = resp.y * h

                        success = when (resp.action) {
                            "click" -> webController.clickAtCoordinate(pixelX, pixelY).success
                            "input" -> webController.inputAtCoordinate(pixelX, pixelY, step.value ?: "").success
                            else -> false
                        }
                    }
                }
                is VisionResult.Error -> {
                    logger.error(step.id, "Vision 失败: ${result.message}")
                }
            }
        }
        return success
    }

    // ── LLM 修复 ──

    private suspend fun tryLLMFix(task: AgentTask, failedStep: TaskStep, currentIndex: Int): Boolean {
        logger.warn(failedStep.id, "尝试 LLM 修复任务...")

        var fixed = false
        planner.fixTask(task, failedStep, failedStep.error ?: "未知错误").collect { result ->
            when (result) {
                is PlanResult.Planning -> {
                    logger.info(failedStep.id, "LLM 正在修复...")
                }
                is PlanResult.Success -> {
                    val fixedTask = result.task
                    logger.success(failedStep.id, "任务已修复: ${fixedTask.steps.size} 步")
                    // 用修复后的任务继续执行（从当前步骤开始）
                    stateMachine.setTask(fixedTask.copy(
                        currentStepIndex = currentIndex,
                        state = TaskState.RUNNING
                    ))
                    fixed = true
                }
                is PlanResult.Error -> {
                    logger.error(failedStep.id, "修复失败: ${result.message}")
                }
            }
        }
        return fixed
    }

    fun destroy() {
        executionJob?.cancel()
        engineScope.cancel()
    }
}
