package com.xiaomi.mimoclaw.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaomi.mimoclaw.agent.log.StructuredLogger
import com.xiaomi.mimoclaw.data.model.*
import com.xiaomi.mimoclaw.platform.agent.*
import com.xiaomi.mimoclaw.platform.billing.*
import com.xiaomi.mimoclaw.platform.cloud.Orchestrator
import com.xiaomi.mimoclaw.platform.event.*
import com.xiaomi.mimoclaw.platform.queue.*
import com.xiaomi.mimoclaw.platform.tenant.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val orchestrator: Orchestrator,
    private val plannerAgent: PlannerAgent,
    private val workerAgent: WorkerAgent,
    private val criticAgent: CriticAgent,
    private val eventBus: AgentEventBus,
    private val taskQueue: TaskQueue,
    private val tenantManager: TenantManager,
    private val billingTracker: BillingTracker,
    private val logger: StructuredLogger
) : ViewModel() {

    // ── 核心状态 ──
    val currentTask: StateFlow<AgentTask?> = orchestrator.activeTasks.map { 
        it.values.firstOrNull()?.task 
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val logs: StateFlow<List<AgentLog>> = logger.logs
    val logText: StateFlow<String> = logger.logText

    // ── Agent Loop 状态 ──
    private val _loopState = MutableStateFlow(LoopState())
    val loopState: StateFlow<LoopState> = _loopState.asStateFlow()

    private val _observations = MutableStateFlow<List<Observation>>(emptyList())
    val observations: StateFlow<List<Observation>> = _observations.asStateFlow()

    private val _checkpoint = MutableStateFlow<TaskCheckpoint?>(null)
    val checkpoint: StateFlow<TaskCheckpoint?> = _checkpoint.asStateFlow()

    // ── Queue 状态 ──
    val queueStats: StateFlow<QueueStats> = taskQueue.stats
    val queuedTasks: StateFlow<List<QueuedTask>> = taskQueue.tasks

    // ── Event 状态 ──
    val eventHistory: StateFlow<List<AgentMessage>> = eventBus.history

    // ── Tenant 状态 ──
    val currentUser: StateFlow<TenantUser?> = tenantManager.currentUser
    val quota: StateFlow<UserQuota?> = tenantManager.quota

    // ── Billing 状态 ──
    val dailyUsage: StateFlow<DailyUsage> = billingTracker.dailyUsage
    val usageSummary: StateFlow<UsageSummary> = billingTracker.records.map { records ->
        val userId = tenantManager.currentUser.value?.userId ?: ""
        billingTracker.userUsage(userId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UsageSummary(0, 0, 0, 0.0))

    // ── UI 状态 ──
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _showDebugPanel = MutableStateFlow(false)
    val showDebugPanel: StateFlow<Boolean> = _showDebugPanel.asStateFlow()

    // ── 操作 ──

    fun updateInput(text: String) {
        _inputText.value = text
    }

    fun execute() {
        val input = _inputText.value.trim()
        if (input.isEmpty()) return
        _inputText.value = ""

        // 通过Orchestrator提交
        val userId = tenantManager.currentUser.value?.userId ?: "local"
        orchestrator.submitTask(input, userId)
    }

    fun pause() {
        val taskId = currentTask.value?.id ?: return
        orchestrator.pauseTask(taskId)
    }

    fun resume() {
        val taskId = currentTask.value?.id ?: return
        orchestrator.resumeTask(taskId)
    }

    fun cancel() {
        val taskId = currentTask.value?.id ?: return
        orchestrator.cancelTask(taskId)
    }

    fun retry() {
        val taskId = currentTask.value?.id ?: return
        orchestrator.submitTask(
            currentTask.value?.description ?: "",
            tenantManager.currentUser.value?.userId ?: "local"
        )
    }

    // Queue操作
    fun pauseTask(taskId: String) = orchestrator.pauseTask(taskId)
    fun resumeTask(taskId: String) = orchestrator.resumeTask(taskId)
    fun cancelTask(taskId: String) = orchestrator.cancelTask(taskId)

    fun toggleDebugPanel() {
        _showDebugPanel.value = !_showDebugPanel.value
    }

    fun clearLogs() {
        logger.clear()
    }

    override fun onCleared() {
        super.onCleared()
        orchestrator.destroy()
    }
}
