package com.xiaomi.mimoclaw.agent.log

import com.xiaomi.mimoclaw.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 结构化日志系统
 * 输出格式: [时间] [级别] [状态] 步骤ID - 消息
 */
@Singleton
class StructuredLogger @Inject constructor() {

    private val _logs = MutableStateFlow<List<AgentLog>>(emptyList())
    val logs: StateFlow<List<AgentLog>> = _logs.asStateFlow()

    private val _logText = MutableStateFlow("")
    val logText: StateFlow<String> = _logText.asStateFlow()

    fun log(stepId: String, level: LogLevel, status: LogStatus, message: String, data: String? = null) {
        val entry = AgentLog(
            stepId = stepId,
            timestamp = System.currentTimeMillis(),
            level = level,
            status = status,
            message = message,
            data = data
        )
        _logs.value = _logs.value + entry
        _logText.value = _logText.value + formatLog(entry) + "\n"
    }

    fun info(stepId: String, message: String) {
        log(stepId, LogLevel.INFO, LogStatus.RUNNING, message)
    }

    fun success(stepId: String, message: String) {
        log(stepId, LogLevel.INFO, LogStatus.SUCCESS, message)
    }

    fun error(stepId: String, message: String) {
        log(stepId, LogLevel.ERROR, LogStatus.FAILED, message)
    }

    fun warn(stepId: String, message: String) {
        log(stepId, LogLevel.WARN, LogStatus.RETRYING, message)
    }

    fun clear() {
        _logs.value = emptyList()
        _logText.value = ""
    }

    private fun formatLog(entry: AgentLog): String {
        val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date(entry.timestamp))
        val level = when (entry.level) {
            LogLevel.INFO -> "INFO "
            LogLevel.WARN -> "WARN "
            LogLevel.ERROR -> "ERROR"
            LogLevel.DEBUG -> "DEBUG"
        }
        val status = when (entry.status) {
            LogStatus.STARTED -> "▶"
            LogStatus.RUNNING -> "⟳"
            LogStatus.SUCCESS -> "✓"
            LogStatus.FAILED -> "✗"
            LogStatus.RETRYING -> "↻"
            LogStatus.SKIPPED -> "⊘"
        }
        return "[$time] [$level] $status ${entry.stepId.take(8)} - ${entry.message}"
    }
}
