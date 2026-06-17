package com.xiaomi.mimoclaw.platform.cloud

import com.xiaomi.mimoclaw.agent.log.StructuredLogger
import com.xiaomi.mimoclaw.data.model.*
import com.xiaomi.mimoclaw.platform.agent.*
import com.xiaomi.mimoclaw.platform.event.*
import com.xiaomi.mimoclaw.platform.queue.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrator - 云端任务调度器
 * 职责: 分配任务给Agent / 监控执行 / 重试/兜底路由
 * 
 * 执行流程:
 * 用户输入 → PlannerAgent → Task DAG → Queue → WorkerAgent → CriticAgent → Vision(可选) → 完成
 */
@Singleton
class Orchestrator @Inject constructor(
    private val plannerAgent: PlannerAgent,
    private val workerAgent: WorkerAgent,
    private val criticAgent: CriticAgent,
    private val taskQueue: TaskQueue,
    private val eventBus: AgentEventBus,
    private val logger: StructuredLogger
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var processingJob: Job? = null
    
    val queueStats: StateFlow<QueueStats> = taskQueue.stats
    val eventHistory: StateFlow<List<AgentMessage>> = eventBus.history
    
    private val _activeTasks = MutableStateFlow<Map<String, OrchestratorTaskState>>(emptyMap())
    val activeTasks: StateFlow<Map<String, OrchestratorTaskState>> = _activeTasks.asStateFlow()
    
    /**
     * 提交任务到调度系统
     */
    fun submitTask(instruction: String, userId: String, priority: Int = 0): String {
        val taskId = java.util.UUID.randomUUID().toString()
        
        scope.launch {
            logger.info("orchestrator", "提交任务: $instruction (taskId=$taskId)")
            
            // 1. PlannerAgent 规划
            var plannedTask: AgentTask? = null
            plannerAgent.plan(instruction, taskId).collect { result ->
                when (result) {
                    is com.xiaomi.mimoclaw.agent.planner.PlanResult.Success -> {
                        plannedTask = result.task
                    }
                    else -> {}
                }
            }
            
            val task = plannedTask
            if (task == null) {
                logger.error("orchestrator", "任务规划失败")
                return@launch
            }
            
            // 2. 入队
            val queuedTask = taskQueue.enqueue(task, userId, priority)
            
            // 3. 更新状态
            _activeTasks.value = _activeTasks.value + (taskId to OrchestratorTaskState(
                taskId = taskId,
                task = task,
                queuedTask = queuedTask,
                phase = OrchestratorPhase.QUEUED
            ))
            
            // 4. 开始处理
            processNext()
        }
        
        return taskId
    }
    
    /**
     * 处理队列中的下一个任务
     */
    private fun processNext() {
        processingJob?.cancel()
        processingJob = scope.launch {
            val queuedTask = taskQueue.dequeue() ?: return@launch
            val taskId = queuedTask.id
            
            logger.info("orchestrator", "开始处理任务: ${queuedTask.task.name}")
            
            _activeTasks.value = _activeTasks.value.toMutableMap().apply {
                this[taskId]?.let { 
                    put(taskId, it.copy(phase = OrchestratorPhase.EXECUTING))
                }
            }
            
            try {
                executeTaskWithOrchestration(queuedTask)
            } catch (e: Exception) {
                logger.error("orchestrator", "任务执行异常: ${e.message}")
                taskQueue.fail(taskId, e.message ?: "Unknown error")
            }
            
            // 继续处理下一个
            processNext()
        }
    }
    
    /**
     * 带编排的任务执行
     */
    private suspend fun executeTaskWithOrchestration(queuedTask: QueuedTask) {
        val task = queuedTask.task
        val taskId = queuedTask.id
        
        var currentIndex = 0
        while (currentIndex < task.steps.size) {
            val step = task.steps[currentIndex]
            
            // 发布步骤分配事件
            eventBus.publish(
                agentId = "orchestrator",
                agentType = AgentType.ORCHESTRATOR,
                type = MessageType.STEP_ASSIGNED,
                taskId = taskId,
                payload = MessagePayload(
                    stepIndex = currentIndex,
                    stepType = step.type.name,
                    instruction = step.description
                )
            )
            
            // WorkerAgent 执行
            val workerResult = workerAgent.executeStep(step, currentIndex, taskId)
            
            // CriticAgent 评估
            val decision = criticAgent.evaluate(step, workerResult, task, taskId)
            
            when (decision.action) {
                CriticAction.APPROVE -> {
                    currentIndex++
                }
                
                CriticAction.RETRY -> {
                    delay(decision.waitMs)
                    // 继续循环重试
                }
                
                CriticAction.VISION_FALLBACK -> {
                    // Vision兜底由WorkerAgent处理
                    logger.info("orchestrator", "Vision兜底: Step $currentIndex")
                    currentIndex++ // 暂时跳过，实际应由Vision处理
                }
                
                CriticAction.LLM_REPAIR -> {
                    logger.info("orchestrator", "LLM修复: Step $currentIndex")
                    // LLM修复由CriticAgent处理
                    currentIndex++
                }
                
                CriticAction.SKIP -> {
                    logger.warn("orchestrator", "跳过步骤: Step $currentIndex")
                    currentIndex++
                }
                
                CriticAction.FAIL -> {
                    logger.error("orchestrator", "任务失败: ${decision.reason}")
                    taskQueue.fail(taskId, decision.reason)
                    _activeTasks.value = _activeTasks.value.toMutableMap().apply {
                        this[taskId]?.let {
                            put(taskId, it.copy(
                                phase = OrchestratorPhase.FAILED,
                                error = decision.reason
                            ))
                        }
                    }
                    return
                }
            }
        }
        
        // 全部完成
        taskQueue.complete(taskId)
        _activeTasks.value = _activeTasks.value.toMutableMap().apply {
            this[taskId]?.let {
                put(taskId, it.copy(phase = OrchestratorPhase.COMPLETED))
            }
        }
        logger.info("orchestrator", "任务完成: ${task.name}")
    }
    
    /**
     * 暂停任务
     */
    fun pauseTask(taskId: String) {
        taskQueue.pause(taskId)
        scope.launch {
            eventBus.publish(
                agentId = "orchestrator",
                agentType = AgentType.ORCHESTRATOR,
                type = MessageType.TASK_PAUSED,
                taskId = taskId,
                payload = MessagePayload()
            )
        }
    }
    
    /**
     * 恢复任务
     */
    fun resumeTask(taskId: String) {
        taskQueue.resume(taskId)
        processNext()
    }
    
    /**
     * 取消任务
     */
    fun cancelTask(taskId: String) {
        taskQueue.cancel(taskId)
        scope.launch {
            eventBus.publish(
                agentId = "orchestrator",
                agentType = AgentType.ORCHESTRATOR,
                type = MessageType.TASK_CANCELLED,
                taskId = taskId,
                payload = MessagePayload()
            )
        }
    }
    
    fun destroy() {
        processingJob?.cancel()
        scope.cancel()
    }
}

data class OrchestratorTaskState(
    val taskId: String,
    val task: AgentTask,
    val queuedTask: QueuedTask,
    val phase: OrchestratorPhase,
    val error: String? = null
)

enum class OrchestratorPhase {
    QUEUED,
    PLANNING,
    EXECUTING,
    REPAIRING,
    COMPLETED,
    FAILED
}
