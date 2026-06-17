package com.xiaomi.mimoclaw.platform.event

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent EventBus - Agent间通信总线
 * 所有Agent通过此总线收发消息
 * 支持:
 * - 按AgentType过滤消息
 * - 按MessageType过滤消息
 * - 按TaskId过滤消息
 * - 消息历史记录
 */
@Singleton
class AgentEventBus @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // 全局消息流
    private val _messages = MutableSharedFlow<AgentMessage>(
        replay = 100,
        extraBufferCapacity = 200
    )
    val messages: SharedFlow<AgentMessage> = _messages.asSharedFlow()
    
    // 消息历史
    private val _history = MutableStateFlow<List<AgentMessage>>(emptyList())
    val history: StateFlow<List<AgentMessage>> = _history.asStateFlow()
    
    // 按AgentType过滤
    fun messagesFor(agentType: AgentType): Flow<AgentMessage> {
        return messages.filter { it.agentType == agentType }
    }
    
    // 按MessageType过滤
    fun messagesOfType(type: MessageType): Flow<AgentMessage> {
        return messages.filter { it.type == type }
    }
    
    // 按TaskId过滤
    fun messagesForTask(taskId: String): Flow<AgentMessage> {
        return messages.filter { it.taskId == taskId }
    }
    
    // 按TaskId + AgentType过滤
    fun messagesForTaskAndAgent(taskId: String, agentType: AgentType): Flow<AgentMessage> {
        return messages.filter { it.taskId == taskId && it.agentType == agentType }
    }
    
    // 发送消息
    suspend fun publish(message: AgentMessage) {
        _messages.emit(message)
        _history.value = (_history.value + message).takeLast(500) // 保留最近500条
    }
    
    // 发送快捷方法
    suspend fun publish(
        agentId: String,
        agentType: AgentType,
        type: MessageType,
        taskId: String,
        payload: MessagePayload,
        result: MessageResult? = null
    ) {
        publish(AgentMessage(
            agentId = agentId,
            agentType = agentType,
            type = type,
            taskId = taskId,
            payload = payload,
            result = result
        ))
    }
    
    // 清除历史
    fun clearHistory() {
        _history.value = emptyList()
    }
    
    // 获取最近N条消息
    fun recentMessages(n: Int = 50): List<AgentMessage> {
        return _history.value.takeLast(n)
    }
    
    // 获取指定Task的所有消息
    fun taskMessages(taskId: String): List<AgentMessage> {
        return _history.value.filter { it.taskId == taskId }
    }
}
