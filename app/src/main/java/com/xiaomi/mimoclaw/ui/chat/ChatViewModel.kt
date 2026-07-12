package com.xiaomi.mimoclaw.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaomi.mimoclaw.data.chat.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _conversations = MutableStateFlow<List<ConversationItem>>(emptyList())
    val conversations: StateFlow<List<ConversationItem>> = _conversations.asStateFlow()

    private var streamJob: Job? = null

    init {
        loadConversations()
    }

    /**
     * 发送消息
     */
    fun sendMessage(content: String) {
        if (content.isBlank() || _uiState.value.isStreaming) return

        val userMessage = UiMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = content.trim()
        )

        // 添加用户消息
        val currentMessages = _uiState.value.messages + userMessage
        _uiState.update { it.copy(messages = currentMessages, isStreaming = true) }

        // 创建 assistant 消息占位
        val assistantMessage = UiMessage(
            id = UUID.randomUUID().toString(),
            role = "assistant",
            content = "",
            isStreaming = true
        )
        _uiState.update { it.copy(messages = it.messages + assistantMessage) }

        // 发起流式请求
        streamJob = viewModelScope.launch {
            val apiMessages = currentMessages.map {
                ChatMessage(role = it.role, content = it.content)
            }

            var fullContent = StringBuilder()

            chatRepository.sendMessageStream(apiMessages)
                .collect { event ->
                    when (event) {
                        is StreamEvent.Delta -> {
                            fullContent.append(event.text)
                            updateLastAssistantMessage(fullContent.toString(), isStreaming = true)
                        }
                        is StreamEvent.Done -> {
                            updateLastAssistantMessage(event.fullContent, isStreaming = false)
                            _uiState.update { it.copy(isStreaming = false) }
                        }
                        is StreamEvent.Error -> {
                            updateLastAssistantMessage(
                                content = if (fullContent.isEmpty()) "❌ ${event.message}"
                                else fullContent.toString() + "\n\n❌ ${event.message}",
                                isStreaming = false
                            )
                            _uiState.update { it.copy(isStreaming = false, error = event.message) }
                        }
                        is StreamEvent.Debug -> { /* ignore in production */ }
                    }
                }
        }
    }

    /**
     * 停止当前流式输出
     */
    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        _uiState.update { state ->
            val messages = state.messages.toMutableList()
            val lastIndex = messages.lastIndex
            if (lastIndex >= 0 && messages[lastIndex].isStreaming) {
                messages[lastIndex] = messages[lastIndex].copy(isStreaming = false)
            }
            state.copy(messages = messages, isStreaming = false)
        }
    }

    /**
     * 清空当前对话
     */
    fun clearChat() {
        streamJob?.cancel()
        _uiState.update { ChatUiState() }
    }

    /**
     * 开始新对话
     */
    fun newChat() {
        clearChat()
    }

    /**
     * 加载对话列表
     */
    fun loadConversations() {
        viewModelScope.launch {
            chatRepository.getConversations()
                .onSuccess { _conversations.value = it }
        }
    }

    /**
     * 删除对话
     */
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conversationId)
            loadConversations()
        }
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 更新最后一条 assistant 消息
     */
    private fun updateLastAssistantMessage(content: String, isStreaming: Boolean) {
        _uiState.update { state ->
            val messages = state.messages.toMutableList()
            val lastIndex = messages.indexOfLast { it.role == "assistant" }
            if (lastIndex >= 0) {
                messages[lastIndex] = messages[lastIndex].copy(
                    content = content,
                    isStreaming = isStreaming
                )
            }
            state.copy(messages = messages)
        }
    }
}

/**
 * Chat 页面 UI 状态
 */
data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val error: String? = null
) {
    val isEmpty: Boolean get() = messages.isEmpty()
    val canSend: Boolean get() = !isStreaming
}
