package com.xiaomi.mimoclaw.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaomi.mimoclaw.data.chat.*
import com.xiaomi.mimoclaw.data.local.LocalChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val localRepository: LocalChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _conversations = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val conversations: StateFlow<List<ConversationSummary>> = _conversations.asStateFlow()

    private var streamJob: Job? = null
    private var currentConversationId: String? = null

    init {
        // 观察本地对话列表
        viewModelScope.launch {
            localRepository.getAllConversations().collect { entities ->
                _conversations.value = entities.map {
                    ConversationSummary(
                        id = it.id,
                        title = it.title,
                        updatedAt = it.updatedAt
                    )
                }
            }
        }
    }

    /**
     * 发送消息
     */
    fun sendMessage(content: String) {
        if (content.isBlank() || _uiState.value.isStreaming) return

        viewModelScope.launch {
            // 如果没有当前对话，创建一个新的
            if (currentConversationId == null) {
                val title = content.trim().take(20).replace("\n", " ")
                val id = localRepository.createConversation(title)
                currentConversationId = id
            }

            val convId = currentConversationId!!

            // 保存用户消息到本地
            val userMsgId = localRepository.addMessage(convId, "user", content.trim())

            val userMessage = UiMessage(
                id = userMsgId,
                role = "user",
                content = content.trim()
            )

            // 更新 UI
            val currentMessages = _uiState.value.messages + userMessage
            _uiState.update { it.copy(messages = currentMessages, isStreaming = true) }

            // 创建 assistant 消息占位
            val assistantMsgId = UUID.randomUUID().toString()
            val assistantMessage = UiMessage(
                id = assistantMsgId,
                role = "assistant",
                content = "",
                isStreaming = true
            )
            _uiState.update { it.copy(messages = it.messages + assistantMessage) }

            // 发起流式请求
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
                            // 保存 AI 回复到本地
                            localRepository.addMessage(convId, "assistant", event.fullContent)
                        }
                        is StreamEvent.Error -> {
                            val errorContent = if (fullContent.isEmpty()) "❌ ${event.message}"
                            else fullContent.toString() + "\n\n❌ ${event.message}"
                            updateLastAssistantMessage(errorContent, isStreaming = false)
                            _uiState.update { it.copy(isStreaming = false, error = event.message) }
                            if (fullContent.isNotEmpty()) {
                                localRepository.addMessage(convId, "assistant", fullContent.toString())
                            }
                        }
                        is StreamEvent.Debug -> { /* ignore */ }
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
     * 开始新对话
     */
    fun newChat() {
        streamJob?.cancel()
        currentConversationId = null
        _uiState.update { ChatUiState() }
    }

    /**
     * 加载历史对话
     */
    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            currentConversationId = conversationId
            val messages = localRepository.getMessagesList(conversationId)
            _uiState.update {
                it.copy(
                    messages = messages.map { msg ->
                        UiMessage(
                            id = msg.id,
                            role = msg.role,
                            content = msg.content
                        )
                    }
                )
            }
        }
    }

    /**
     * 删除对话
     */
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            localRepository.deleteConversation(conversationId)
            if (currentConversationId == conversationId) {
                newChat()
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

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

data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val error: String? = null
) {
    val isEmpty: Boolean get() = messages.isEmpty()
    val canSend: Boolean get() = !isStreaming
}

/**
 * 对话摘要（用于列表展示）
 */
data class ConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: Long
)
