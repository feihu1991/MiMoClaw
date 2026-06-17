package com.xiaomi.mimoclaw.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaomi.mimoclaw.agent.engine.AgentEngine
import com.xiaomi.mimoclaw.agent.log.StructuredLogger
import com.xiaomi.mimoclaw.data.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val engine: AgentEngine,
    private val logger: StructuredLogger
) : ViewModel() {

    val currentTask: StateFlow<AgentTask?> = engine.currentTask
    val logs: StateFlow<List<AgentLog>> = engine.logs
    val logText: StateFlow<String> = logger.logText

    // UI 状态
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _showBrowser = MutableStateFlow(false)
    val showBrowser: StateFlow<Boolean> = _showBrowser.asStateFlow()

    fun updateInput(text: String) {
        _inputText.value = text
    }

    fun execute() {
        val input = _inputText.value.trim()
        if (input.isEmpty()) return
        _inputText.value = ""
        engine.executeFromInstruction(input)
    }

    fun pause() = engine.pause()
    fun resume() = engine.resume()
    fun cancel() = engine.cancel()
    fun retry() = engine.retry()

    fun toggleBrowser() {
        _showBrowser.value = !_showBrowser.value
    }

    fun clearLogs() {
        logger.clear()
    }

    override fun onCleared() {
        super.onCleared()
        engine.destroy()
    }
}
