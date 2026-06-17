package com.xiaomi.mimoclaw.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaomi.mimoclaw.agent.log.StructuredLogger
import com.xiaomi.mimoclaw.agent.loop.AgentLoopEngine
import com.xiaomi.mimoclaw.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val loopEngine: AgentLoopEngine,
    private val logger: StructuredLogger
) : ViewModel() {

    // 核心状态
    val currentTask: StateFlow<AgentTask?> = loopEngine.currentTask
    val logs: StateFlow<List<AgentLog>> = loopEngine.logs
    val logText: StateFlow<String> = logger.logText

    // Agent Loop 状态
    val loopState: StateFlow<LoopState> = loopEngine.loopState
    val observations: StateFlow<List<Observation>> = loopEngine.observations
    val checkpoint: StateFlow<TaskCheckpoint?> = loopEngine.checkpoint

    // UI 状态
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _showDebugPanel = MutableStateFlow(false)
    val showDebugPanel: StateFlow<Boolean> = _showDebugPanel.asStateFlow()

    private val _showBrowser = MutableStateFlow(false)
    val showBrowser: StateFlow<Boolean> = _showBrowser.asStateFlow()

    fun updateInput(text: String) {
        _inputText.value = text
    }

    fun execute() {
        val input = _inputText.value.trim()
        if (input.isEmpty()) return
        _inputText.value = ""
        loopEngine.executeFromInstruction(input)
    }

    fun pause() = loopEngine.pause()
    fun resume() = loopEngine.resume()
    fun cancel() = loopEngine.cancel()
    fun retry() = loopEngine.retry()

    fun toggleDebugPanel() {
        _showDebugPanel.value = !_showDebugPanel.value
    }

    fun toggleBrowser() {
        _showBrowser.value = !_showBrowser.value
    }

    fun clearLogs() {
        logger.clear()
    }

    override fun onCleared() {
        super.onCleared()
        loopEngine.destroy()
    }
}
