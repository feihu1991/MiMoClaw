package com.xiaomi.mimoclaw.agent.state

import com.xiaomi.mimoclaw.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务状态机 - 管理任务和步骤的状态转换
 *
 * 有效状态转换:
 *  IDLE → RUNNING
 *  RUNNING → PAUSED | FAILED | SUCCESS | CANCELLED
 *  PAUSED → RUNNING | CANCELLED
 *  FAILED → RUNNING (重试)
 *  SUCCESS → 终态
 *  CANCELLED → 终态
 */
@Singleton
class TaskStateMachine @Inject constructor() {

    private val _currentTask = MutableStateFlow<AgentTask?>(null)
    val currentTask: StateFlow<AgentTask?> = _currentTask.asStateFlow()

    private val _logs = MutableStateFlow<List<AgentLog>>(emptyList())
    val logs: StateFlow<List<AgentLog>> = _logs.asStateFlow()

    fun setTask(task: AgentTask) {
        _currentTask.value = task
        _logs.value = task.logs
    }

    fun createTask(name: String, steps: List<TaskStep>): AgentTask {
        val task = AgentTask(name = name, steps = steps)
        _currentTask.value = task
        _logs.value = emptyList()
        return task
    }

    // ── 任务级状态转换 ──

    fun startTask(): Boolean {
        val task = _currentTask.value ?: return false
        if (task.state != TaskState.IDLE && task.state != TaskState.PAUSED && task.state != TaskState.FAILED) {
            return false
        }
        updateTask(task.copy(state = TaskState.RUNNING, updatedAt = System.currentTimeMillis()))
        addLog(task.id, LogLevel.INFO, LogStatus.STARTED, "任务开始: ${task.name}")
        return true
    }

    fun pauseTask(): Boolean {
        val task = _currentTask.value ?: return false
        if (task.state != TaskState.RUNNING) return false
        updateTask(task.copy(state = TaskState.PAUSED, updatedAt = System.currentTimeMillis()))
        addLog(task.id, LogLevel.INFO, LogStatus.RUNNING, "任务已暂停")
        return true
    }

    fun resumeTask(): Boolean {
        val task = _currentTask.value ?: return false
        if (task.state != TaskState.PAUSED) return false
        updateTask(task.copy(state = TaskState.RUNNING, updatedAt = System.currentTimeMillis()))
        addLog(task.id, LogLevel.INFO, LogStatus.RUNNING, "任务恢复执行")
        return true
    }

    fun failTask(error: String): Boolean {
        val task = _currentTask.value ?: return false
        updateTask(task.copy(
            state = TaskState.FAILED,
            error = error,
            updatedAt = System.currentTimeMillis()
        ))
        addLog(task.id, LogLevel.ERROR, LogStatus.FAILED, "任务失败: $error")
        return true
    }

    fun completeTask(): Boolean {
        val task = _currentTask.value ?: return false
        updateTask(task.copy(state = TaskState.SUCCESS, updatedAt = System.currentTimeMillis()))
        addLog(task.id, LogLevel.INFO, LogStatus.SUCCESS, "任务完成")
        return true
    }

    fun cancelTask(): Boolean {
        val task = _currentTask.value ?: return false
        updateTask(task.copy(state = TaskState.CANCELLED, updatedAt = System.currentTimeMillis()))
        addLog(task.id, LogLevel.WARN, LogStatus.SKIPPED, "任务已取消")
        return true
    }

    // ── 步骤级状态转换 ──

    fun startStep(stepIndex: Int): Boolean {
        val task = _currentTask.value ?: return false
        if (stepIndex < 0 || stepIndex >= task.steps.size) return false

        val steps = task.steps.toMutableList()
        steps[stepIndex] = steps[stepIndex].copy(state = StepState.RUNNING)
        updateTask(task.copy(
            steps = steps,
            currentStepIndex = stepIndex,
            updatedAt = System.currentTimeMillis()
        ))
        addLog(steps[stepIndex].id, LogLevel.INFO, LogStatus.STARTED,
            "步骤 ${stepIndex + 1}/${task.steps.size}: ${steps[stepIndex].description}")
        return true
    }

    fun completeStep(stepIndex: Int, result: String? = null): Boolean {
        val task = _currentTask.value ?: return false
        if (stepIndex < 0 || stepIndex >= task.steps.size) return false

        val steps = task.steps.toMutableList()
        steps[stepIndex] = steps[stepIndex].copy(
            state = StepState.SUCCESS,
            result = result
        )
        updateTask(task.copy(steps = steps, updatedAt = System.currentTimeMillis()))
        addLog(steps[stepIndex].id, LogLevel.INFO, LogStatus.SUCCESS,
            "步骤完成: ${steps[stepIndex].description}")
        return true
    }

    fun failStep(stepIndex: Int, error: String): Boolean {
        val task = _currentTask.value ?: return false
        if (stepIndex < 0 || stepIndex >= task.steps.size) return false

        val steps = task.steps.toMutableList()
        val step = steps[stepIndex]
        steps[stepIndex] = step.copy(
            state = StepState.FAILED,
            error = error,
            retryCount = step.retryCount + 1
        )
        updateTask(task.copy(steps = steps, updatedAt = System.currentTimeMillis()))
        addLog(step.id, LogLevel.ERROR, LogStatus.FAILED,
            "步骤失败: ${step.description} - $error (重试 ${step.retryCount}/${step.maxRetries})")
        return true
    }

    fun retryStep(stepIndex: Int): Boolean {
        val task = _currentTask.value ?: return false
        if (stepIndex < 0 || stepIndex >= task.steps.size) return false

        val step = task.steps[stepIndex]
        if (step.retryCount >= step.maxRetries) return false

        val steps = task.steps.toMutableList()
        steps[stepIndex] = step.copy(state = StepState.RETRYING)
        updateTask(task.copy(steps = steps, updatedAt = System.currentTimeMillis()))
        addLog(step.id, LogLevel.WARN, LogStatus.RETRYING,
            "步骤重试: ${step.description} (${step.retryCount}/${step.maxRetries})")
        return true
    }

    // ── 辅助方法 ──

    fun isRunning(): Boolean = _currentTask.value?.state == TaskState.RUNNING
    fun isPaused(): Boolean = _currentTask.value?.state == TaskState.PAUSED

    fun getNextStepIndex(): Int? {
        val task = _currentTask.value ?: return null
        val nextIndex = task.currentStepIndex + 1
        return if (nextIndex < task.steps.size) nextIndex else null
    }

    fun canRetryCurrentStep(): Boolean {
        val task = _currentTask.value ?: return false
        val idx = task.currentStepIndex
        if (idx < 0 || idx >= task.steps.size) return false
        val step = task.steps[idx]
        return step.retryCount < step.maxRetries
    }

    private fun updateTask(task: AgentTask) {
        _currentTask.value = task
    }

    private fun addLog(stepId: String, level: LogLevel, status: LogStatus, message: String, data: String? = null) {
        val log = AgentLog(
            stepId = stepId,
            timestamp = System.currentTimeMillis(),
            level = level,
            status = status,
            message = message,
            data = data
        )
        _logs.value = _logs.value + log
    }
}
