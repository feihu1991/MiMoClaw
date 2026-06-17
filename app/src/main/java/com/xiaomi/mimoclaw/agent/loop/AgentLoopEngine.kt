package com.xiaomi.mimoclaw.agent.loop

import com.xiaomi.mimoclaw.agent.classifier.FailureClassifier
import com.xiaomi.mimoclaw.agent.healing.HealingResult
import com.xiaomi.mimoclaw.agent.healing.SelfHealingSystem
import com.xiaomi.mimoclaw.agent.log.StructuredLogger
import com.xiaomi.mimoclaw.agent.observation.ObservationSystem
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
 * AgentLoopEngine - 核心执行闭环
 *
 * 完整循环: observe() → decide() → act() → verify() → repair()
 *
 * 每一步执行后必须返回 Observation
 * 失败时进入 Repair flow
 * DOM 失败自动切 Vision fallback
 */
@Singleton
class AgentLoopEngine @Inject constructor(
    private val stateMachine: TaskStateMachine,
    private val webController: WebController,
    private val planner: LLMPlanner,
    private val visionAgent: VisionAgent,
    private val observationSystem: ObservationSystem,
    private val failureClassifier: FailureClassifier,
    private val selfHealingSystem: SelfHealingSystem,
    private val logger: StructuredLogger
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var executionJob: Job? = null

    // 公开状态
    val currentTask: StateFlow<AgentTask?> = stateMachine.currentTask
    val logs: StateFlow<List<AgentLog>> = stateMachine.logs

    private val _loopState = MutableStateFlow(LoopState())
    val loopState: StateFlow<LoopState> = _loopState.asStateFlow()

    private val _observations = MutableStateFlow<List<Observation>>(emptyList())
    val observations: StateFlow<List<Observation>> = _observations.asStateFlow()

    private val _checkpoint = MutableStateFlow<TaskCheckpoint?>(null)
    val checkpoint: StateFlow<TaskCheckpoint?> = _checkpoint.asStateFlow()

    // ── 任务生命周期 ──

    fun executeFromInstruction(instruction: String) {
        engineScope.launch {
            logger.info("loop", "收到指令: $instruction")
            updateLoopState(LoopPhase.DECIDING)

            planner.plan(PlanRequest(instruction = instruction)).collect { result ->
                when (result) {
                    is PlanResult.Planning -> {
                        logger.info("loop", "LLM 正在规划任务...")
                    }
                    is PlanResult.Success -> {
                        logger.success("loop", "任务规划完成: ${result.task.name}")
                        executeTask(result.task)
                    }
                    is PlanResult.Error -> {
                        logger.error("loop", "规划失败: ${result.message}")
                        stateMachine.failTask("规划失败: ${result.message}")
                        updateLoopState(LoopPhase.FAILED)
                    }
                }
            }
        }
    }

    fun executeTask(task: AgentTask) {
        executionJob?.cancel()
        executionJob = engineScope.launch {
            stateMachine.setTask(task)
            stateMachine.startTask()
            _observations.value = emptyList()

            // 初始化 checkpoint
            _checkpoint.value = TaskCheckpoint(
                taskId = task.id,
                currentStep = 0,
                completedSteps = emptyList()
            )

            var currentIndex = 0
            while (currentIndex < task.steps.size && stateMachine.isRunning()) {
                val step = task.steps[currentIndex]

                // 检查 checkpoint - 跳过已完成步骤
                val cp = _checkpoint.value
                if (cp != null && cp.isStepCompleted(step.id)) {
                    logger.info(step.id, "步骤已完成，跳过")
                    stateMachine.startStep(currentIndex)
                    stateMachine.completeStep(currentIndex, cp.stepResults[step.id])
                    currentIndex++
                    continue
                }

                // 检查暂停
                while (stateMachine.isPaused()) {
                    delay(500)
                }
                if (!stateMachine.isRunning()) break

                // ═══ 核心闭环: observe → decide → act → verify → repair ═══
                val loopResult = executeLoop(currentIndex, step)

                when (loopResult) {
                    LoopResult.SUCCESS -> {
                        stateMachine.completeStep(currentIndex)
                        updateCheckpoint(currentIndex, step.id, step.result)
                        currentIndex++
                    }
                    LoopResult.SKIPPED -> {
                        stateMachine.startStep(currentIndex)
                        stateMachine.completeStep(currentIndex, "跳过")
                        updateCheckpoint(currentIndex, step.id, "skipped")
                        currentIndex++
                    }
                    LoopResult.FAILED -> {
                        stateMachine.failTask("步骤失败: ${step.description}")
                        updateLoopState(LoopPhase.FAILED)
                        break
                    }
                    LoopResult.RETRY -> {
                        // 继续循环重试当前步骤
                        continue
                    }
                }
            }

            // 全部完成
            if (stateMachine.currentTask.value?.state == TaskState.RUNNING) {
                stateMachine.completeTask()
                updateLoopState(LoopPhase.COMPLETED)
            }
        }
    }

    /**
     * 核心闭环: observe → decide → act → verify → repair
     */
    private suspend fun executeLoop(index: Int, step: TaskStep): LoopResult {
        // 1. OBSERVE - 观察当前页面状态
        updateLoopState(LoopPhase.OBSERVING, index)
        logger.info(step.id, "═══ LOOP: OBSERVE ═══")
        val preObservation = observationSystem.quickObserve(step.id, true)
        logger.info(step.id, "页面: ${preObservation.currentUrl}")

        // 2. DECIDE - 决定执行策略
        updateLoopState(LoopPhase.DECIDING, index)
        logger.info(step.id, "═══ LOOP: DECIDE ═══")
        stateMachine.startStep(index)

        // 3. ACT - 执行步骤
        updateLoopState(LoopPhase.ACTING, index)
        logger.info(step.id, "═══ LOOP: ACT ═══")
        val actionResult = executeStepAction(step)

        // 4. VERIFY - 验证执行结果
        updateLoopState(LoopPhase.VERIFYING, index)
        logger.info(step.id, "═══ LOOP: VERIFY ═══")
        val observation = observationSystem.observe(step.id, actionResult, step)
        _observations.value = _observations.value + observation

        // 更新 loopState 中的 observation
        _loopState.value = _loopState.value.copy(observation = observation)

        if (observation.success) {
            logger.success(step.id, "验证通过 ✓")
            updateLoopState(LoopPhase.IDLE)
            return LoopResult.SUCCESS
        }

        // 5. REPAIR - 失败修复
        logger.warn(step.id, "═══ LOOP: REPAIR ═══")
        updateLoopState(LoopPhase.REPAIRING, index)

        // 分类错误
        val classification = failureClassifier.classify(step, actionResult, observation, null)
        _loopState.value = _loopState.value.copy(failureClassification = classification)
        logger.warn(step.id, "错误分类: ${classification.type} - ${classification.message}")

        // 根据错误类型选择修复策略
        return when {
            // DOM 未找到 → Vision fallback
            classification.shouldUseVision -> {
                val visionSuccess = tryVisionFallback(step, observation)
                if (visionSuccess) {
                    logger.success(step.id, "Vision 兜底成功 ✓")
                    _loopState.value = _loopState.value.copy(visionFallbackUsed = true)
                    updateLoopState(LoopPhase.IDLE)
                    LoopResult.SUCCESS
                } else {
                    // Vision 也失败 → 尝试 LLM repair
                    tryLLMRepair(step, observation, classification, index)
                }
            }

            // 需要重试
            classification.shouldRetry && step.retryCount < step.maxRetries -> {
                logger.warn(step.id, "等待 ${classification.waitBeforeRetryMs}ms 后重试...")
                delay(classification.waitBeforeRetryMs)
                stateMachine.retryStep(index)
                LoopResult.RETRY
            }

            // 需要 LLM 修复
            classification.shouldUseLLMRepair -> {
                tryLLMRepair(step, observation, classification, index)
            }

            // 其他 → 重试或失败
            step.retryCount < step.maxRetries -> {
                stateMachine.retryStep(index)
                LoopResult.RETRY
            }

            else -> {
                logger.error(step.id, "所有修复策略均失败")
                LoopResult.FAILED
            }
        }
    }

    // ── 步骤执行 ──

    private suspend fun executeStepAction(step: TaskStep): ActionResult {
        return try {
            when (step.type) {
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
                StepType.VISION_CLICK -> ActionResult(false, "需要 Vision 处理")
                StepType.VISION_INPUT -> ActionResult(false, "需要 Vision 处理")
                StepType.LLM_DECISION -> ActionResult(true, "LLM 决策点")
                StepType.CONDITIONAL -> ActionResult(true, "条件分支")
            }
        } catch (e: Exception) {
            ActionResult(false, "异常: ${e.message}")
        }
    }

    // ── Vision 兜底 ──

    private suspend fun tryVisionFallback(step: TaskStep, observation: Observation): Boolean {
        logger.info(step.id, "尝试 Vision 兜底...")

        val screenshot = observation.screenshot ?: run {
            val result = webController.screenshot()
            result.data
        } ?: return false

        val instruction = when (step.type) {
            StepType.CLICK -> "找到并点击: ${step.description}"
            StepType.INPUT -> "找到输入框: ${step.description}"
            else -> step.description
        }

        var success = false
        visionAgent.analyze(VisionRequest(
            screenshot = screenshot,
            instruction = instruction,
            currentUrl = observation.currentUrl
        )).collect { result ->
            when (result) {
                is VisionResult.Analyzing -> {
                    logger.info(step.id, "Vision 分析中...")
                }
                is VisionResult.Success -> {
                    val resp = result.response
                    logger.info(step.id, "Vision 结果: ${resp.description} (置信度: ${resp.confidence})")

                    if (resp.confidence > 0.3f) {
                        val pixelX = resp.x * 1080f
                        val pixelY = resp.y * 1920f

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

    private suspend fun tryLLMRepair(
        step: TaskStep,
        observation: Observation,
        classification: FailureClassification,
        currentIndex: Int
    ): LoopResult {
        logger.info(step.id, "尝试 LLM 修复...")
        _loopState.value = _loopState.value.copy(llmRepairUsed = true)

        val task = stateMachine.currentTask.value ?: return LoopResult.FAILED

        val repairRequest = RepairRequest(
            originalTask = task,
            failedStep = step,
            observation = observation,
            failureClassification = classification,
            checkpoint = _checkpoint.value ?: TaskCheckpoint(taskId = task.id, currentStep = currentIndex)
        )

        var result = LoopResult.FAILED
        selfHealingSystem.repair(repairRequest).collect { healingResult ->
            when (healingResult) {
                is HealingResult.Analyzing -> {
                    logger.info(step.id, "LLM 正在分析修复方案...")
                }
                is HealingResult.Repaired -> {
                    val response = healingResult.response
                    logger.info(step.id, "修复方案: ${response.explanation}")

                    if (response.shouldSkip) {
                        logger.warn(step.id, "跳过该步骤")
                        result = LoopResult.SKIPPED
                    } else if (response.repairedStep != null) {
                        // 用修复后的步骤替换原步骤
                        val steps = task.steps.toMutableList()
                        steps[currentIndex] = response.repairedStep.copy(
                            retryCount = step.retryCount + 1
                        )
                        stateMachine.setTask(task.copy(steps = steps))
                        stateMachine.retryStep(currentIndex)
                        result = LoopResult.RETRY
                    } else if (response.shouldRetry) {
                        stateMachine.retryStep(currentIndex)
                        result = LoopResult.RETRY
                    }
                }
                is HealingResult.Failed -> {
                    logger.error(step.id, "修复失败: ${healingResult.message}")
                }
            }
        }
        return result
    }

    // ── Checkpoint 管理 ──

    private fun updateCheckpoint(stepIndex: Int, stepId: String, result: String?) {
        val cp = _checkpoint.value ?: return
        _checkpoint.value = cp.copy(
            currentStep = stepIndex + 1,
            completedSteps = cp.completedSteps + stepId,
            stepResults = cp.stepResults + (stepId to (result ?: ""))
        )
    }

    fun saveCheckpoint(): TaskCheckpoint? {
        return _checkpoint.value
    }

    fun resumeFromCheckpoint(checkpoint: TaskCheckpoint) {
        val task = stateMachine.currentTask.value ?: return
        _checkpoint.value = checkpoint
        executeTask(task)
    }

    // ── 控制方法 ──

    fun pause() = stateMachine.pauseTask()
    fun resume() = stateMachine.resumeTask()

    fun cancel() {
        executionJob?.cancel()
        stateMachine.cancelTask()
        updateLoopState(LoopPhase.IDLE)
    }

    fun retry() {
        val task = stateMachine.currentTask.value ?: return
        executeTask(task.copy(retryCount = task.retryCount + 1))
    }

    private fun updateLoopState(phase: LoopPhase, stepIndex: Int = -1) {
        _loopState.value = _loopState.value.copy(
            phase = phase,
            currentStepIndex = stepIndex,
            timestamp = System.currentTimeMillis()
        )
    }

    fun destroy() {
        executionJob?.cancel()
        engineScope.cancel()
    }
}

private enum class LoopResult {
    SUCCESS,
    FAILED,
    RETRY,
    SKIPPED
}
