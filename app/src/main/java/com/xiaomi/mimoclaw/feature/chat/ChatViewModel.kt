package com.xiaomi.mimoclaw.feature.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaomi.mimoclaw.core.network.ClawGateway
import com.xiaomi.mimoclaw.core.network.ConnectionState
import com.xiaomi.mimoclaw.core.storage.ConversationStore
import com.xiaomi.mimoclaw.core.network.ContentBlock
import com.xiaomi.mimoclaw.core.network.GatewayEvent
import com.xiaomi.mimoclaw.core.network.SessionInfo
import com.xiaomi.mimoclaw.feature.chat.model.ChatMessage
import com.xiaomi.mimoclaw.feature.chat.model.DisplayItem
import com.xiaomi.mimoclaw.feature.chat.model.ToolCall
import com.xiaomi.mimoclaw.feature.chat.model.Conversation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val gateway: ClawGateway,
    private val conversationStore: ConversationStore
) : ViewModel() {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()

    private val _currentModel = MutableStateFlow(DEFAULT_MODEL)
    val currentModel: StateFlow<String> = _currentModel.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ── 折叠状态管理 ──
    private val _collapsedGroups = MutableStateFlow<Set<String>>(emptySet())

    // ── 显示项列表（将消息分组为可渲染的扁平列表）──
    val displayItems: StateFlow<List<DisplayItem>> = combine(
        _currentConversation,
        _collapsedGroups
    ) { conversation, _ ->
        conversation?.messages.toDisplayItems()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleGroup(groupId: String) {
        _collapsedGroups.update { current ->
            if (groupId in current) current - groupId else current + groupId
        }
    }

    fun isGroupCollapsed(groupId: String): Boolean = groupId in _collapsedGroups.value

    private var streamingSessionKey: String? = null
    private var streamingMessageId: String? = null

    init {
        // 加载持久化的对话
        viewModelScope.launch(Dispatchers.IO) {
            val saved = conversationStore.load()
            if (saved.isNotEmpty()) {
                _conversations.value = saved
            }
        }
        viewModelScope.launch {
            gateway.events.collect(::handleGatewayEvent)
        }
        viewModelScope.launch {
            gateway.connectionState.collect { _connectionState.value = it }
        }
        connect()
    }

    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTING) return
        viewModelScope.launch(Dispatchers.IO) {
            gateway.connect().onFailure { error ->
                Log.e(TAG, "Gateway 连接失败: ${error.message}")
            }
        }
    }

    fun disconnect() = gateway.disconnect()

    fun sendMessage(text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty() || _isStreaming.value) return

        val base = _currentConversation.value ?: Conversation(
            title = normalized.take(30),
            model = _currentModel.value,
            sessionKey = gateway.createDashboardSessionKey()
        )
        val sessionKey = requireNotNull(base.sessionKey)
        val assistant = ChatMessage(
            role = ChatMessage.Role.ASSISTANT,
            content = "",
            isStreaming = true
        )
        val updated = base.copy(
            messages = base.messages +
                ChatMessage(role = ChatMessage.Role.USER, content = normalized) +
                assistant
        )

        gateway.setSessionKey(sessionKey)
        upsertConversation(updated, makeCurrent = true)
        streamingSessionKey = sessionKey
        streamingMessageId = assistant.id
        _isStreaming.value = true

        // 新消息发送时自动折叠之前的工具组
        _collapsedGroups.value = updated.messages
            .filter { it.role == ChatMessage.Role.ASSISTANT && it.toolCalls.isNotEmpty() }
            .map { "${it.id}_tools" }
            .toSet()

        viewModelScope.launch(Dispatchers.IO) {
            gateway.sendChatMessage(
                message = normalized,
                model = _currentModel.value,
                sessionKey = sessionKey
            ).onFailure { error ->
                finishStreaming(sessionKey, assistant.id, "发送失败: ${error.message}")
            }
        }
    }

    fun abortChat() {
        val sessionKey = streamingSessionKey ?: return
        val messageId = streamingMessageId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            gateway.abortChat(sessionKey)
            finishStreaming(sessionKey, messageId, null)
        }
    }

    private fun handleGatewayEvent(event: GatewayEvent) {
        when (event) {
            is GatewayEvent.AgentEvent -> applyContentBlocks(
                sessionKey = event.sessionKey,
                blocks = event.blocks,
                isComplete = false
            )
            is GatewayEvent.ChatEvent -> Unit // 与 AgentEvent 内容重叠
            is GatewayEvent.SessionMessage -> applyContentBlocks(
                sessionKey = event.sessionKey,
                blocks = event.blocks,
                isComplete = true
            )
            is GatewayEvent.ToolEvent -> addToolEvent(event)
            is GatewayEvent.SessionList -> syncSessionsFromServer(event.sessions)
        }
    }

    private fun applyContentBlocks(
        sessionKey: String?,
        blocks: List<ContentBlock>,
        isComplete: Boolean
    ) {
        if (blocks.isEmpty()) return
        val targetSession = sessionKey ?: streamingSessionKey ?: return
        if (streamingSessionKey != null && targetSession != streamingSessionKey) return

        val conversation = findConversation(targetSession) ?: return
        val messageId = streamingMessageId ?: return
        val index = conversation.messages.indexOfFirst { it.id == messageId && it.isStreaming }
        if (index < 0) return

        val oldMessage = conversation.messages[index]
        var thinking = if (isComplete) "" else oldMessage.thinkingContent
        var content = if (isComplete) "" else oldMessage.content
        var tools = if (isComplete) emptyList() else oldMessage.toolCalls
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.Thinking -> thinking += block.text
                is ContentBlock.Text -> content += block.text
                is ContentBlock.Tool -> tools = mergeToolCall(tools, block.name, block.status)
            }
        }

        val newMessages = conversation.messages.toMutableList().apply {
            this[index] = oldMessage.copy(
                thinkingContent = thinking,
                toolCalls = tools,
                content = content,
                isStreaming = !isComplete
            )
        }
        upsertConversation(conversation.copy(messages = newMessages), makeCurrent = false)

        if (isComplete) clearStreamingState(targetSession, messageId)
    }

    private fun addToolEvent(event: GatewayEvent.ToolEvent) {
        val sessionKey = event.sessionKey ?: streamingSessionKey ?: return
        if (streamingSessionKey != null && sessionKey != streamingSessionKey) return
        val conversation = findConversation(sessionKey) ?: return
        val messageId = streamingMessageId ?: return
        val index = conversation.messages.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val messages = conversation.messages.toMutableList()
        val assistant = messages[index]
        messages[index] = assistant.copy(toolCalls = mergeToolCall(assistant.toolCalls, event.name, event.status))
        upsertConversation(conversation.copy(messages = messages), makeCurrent = false)
    }

    private fun mergeToolCall(current: List<ToolCall>, name: String, status: String): List<ToolCall> {
        val index = current.indexOfLast { it.name == name }
        return if (index < 0) current + ToolCall(name, status)
        else current.toMutableList().apply { this[index] = ToolCall(name, status) }
    }

    private fun finishStreaming(sessionKey: String, messageId: String, errorMessage: String?) {
        val conversation = findConversation(sessionKey) ?: run {
            clearStreamingState(sessionKey, messageId)
            return
        }
        val index = conversation.messages.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            val oldMessage = conversation.messages[index]
            val newMessages = conversation.messages.toMutableList().apply {
                this[index] = oldMessage.copy(
                    content = errorMessage ?: oldMessage.content,
                    isStreaming = false
                )
            }
            upsertConversation(conversation.copy(messages = newMessages), makeCurrent = false)
        }
        clearStreamingState(sessionKey, messageId)
    }

    private fun clearStreamingState(sessionKey: String, messageId: String) {
        if (streamingSessionKey == sessionKey && streamingMessageId == messageId) {
            streamingSessionKey = null
            streamingMessageId = null
            _isStreaming.value = false
        }
    }

    private fun findConversation(sessionKey: String): Conversation? =
        _currentConversation.value?.takeIf { it.sessionKey == sessionKey }
            ?: _conversations.value.firstOrNull { it.sessionKey == sessionKey }

    private fun upsertConversation(conversation: Conversation, makeCurrent: Boolean) {
        _conversations.update { current ->
            val index = current.indexOfFirst {
                it.id == conversation.id || it.sessionKey == conversation.sessionKey
            }
            if (index < 0) {
                listOf(conversation) + current
            } else {
                current.toMutableList().apply { this[index] = conversation }
            }
        }
        if (makeCurrent || _currentConversation.value?.id == conversation.id) {
            _currentConversation.value = conversation
        }
        persistConversations()
    }

    private fun syncSessionsFromServer(sessions: List<SessionInfo>) {
        val existing = _conversations.value.associateBy { it.sessionKey }
        val serverKeys = sessions.mapTo(mutableSetOf()) { it.key }
        val localOnly = _conversations.value.filter { it.sessionKey !in serverKeys && it.messages.isNotEmpty() }
        val serverConversations = sessions.map { session ->
            existing[session.key] ?: Conversation(
                id = session.sessionId.ifEmpty { session.key },
                title = session.title,
                model = session.model,
                sessionKey = session.key
            )
        }
        _conversations.value = localOnly + serverConversations
    }

    fun newConversation() {
        _currentConversation.value = null
    }

    fun selectConversation(conversation: Conversation) {
        _currentConversation.value = conversation
        _currentModel.value = conversation.model
        val sessionKey = conversation.sessionKey ?: return
        gateway.setSessionKey(sessionKey)
        viewModelScope.launch(Dispatchers.IO) {
            gateway.getChatHistory(sessionKey).onSuccess { history ->
                val messages = history.map { message ->
                    ChatMessage(
                        role = when (message.role) {
                            "assistant" -> ChatMessage.Role.ASSISTANT
                            "system" -> ChatMessage.Role.SYSTEM
                            else -> ChatMessage.Role.USER
                        },
                        content = message.content,
                        thinkingContent = message.thinking,
                        toolCalls = message.tools.map { ToolCall(it.name, it.status) }
                    )
                }
                val refreshed = conversation.copy(messages = messages)
                val stillSelected = _currentConversation.value?.sessionKey == sessionKey
                upsertConversation(refreshed, makeCurrent = stillSelected)
            }
        }
    }

    fun deleteConversation(conversation: Conversation) {
        _conversations.update { list -> list.filterNot { it.id == conversation.id } }
        if (_currentConversation.value?.id == conversation.id) _currentConversation.value = null
        if (streamingSessionKey == conversation.sessionKey) abortChat()
        persistConversations()
    }

    fun setModel(model: String) {
        _currentModel.value = model
        _currentConversation.value?.let { current ->
            upsertConversation(current.copy(model = model), makeCurrent = true)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val sessionKey = _currentConversation.value?.sessionKey ?: return@launch
            gateway.setSessionModel(sessionKey = sessionKey, model = model)
                .onFailure { Log.e(TAG, "切换模型失败: ${it.message}") }
        }
    }

    private fun persistConversations() {
        viewModelScope.launch(Dispatchers.IO) {
            conversationStore.save(_conversations.value)
        }
    }

    private companion object {
        const val TAG = "ChatVM"
        const val DEFAULT_MODEL = "mimo-v2.5-pro"
    }
}

/**
 * 将消息列表转换为扁平的显示项列表
 *
 * 分组规则：
 * - USER 消息 → UserMessage
 * - ASSISTANT 消息 → ToolGroup（如有工具调用）+ ThinkingBlock（如有思考过程）+ AssistantMessage
 * - SYSTEM 消息 → AssistantMessage
 */
private fun List<ChatMessage>?.toDisplayItems(): List<DisplayItem> {
    if (this.isNullOrEmpty()) return emptyList()
    val items = mutableListOf<DisplayItem>()
    for (message in this) {
        when (message.role) {
            ChatMessage.Role.USER -> {
                items.add(DisplayItem.UserMessage(message.id, message))
            }
            ChatMessage.Role.ASSISTANT -> {
                if (message.toolCalls.isNotEmpty()) {
                    items.add(DisplayItem.ToolGroup(
                        id = "${message.id}_tools",
                        tools = message.toolCalls,
                        isRunning = message.isStreaming
                    ))
                }
                if (message.thinkingContent.isNotBlank()) {
                    items.add(DisplayItem.ThinkingBlock(
                        id = "${message.id}_thinking",
                        text = message.thinkingContent
                    ))
                }
                if (message.content.isNotBlank() || message.isStreaming) {
                    items.add(DisplayItem.AssistantMessage(message.id, message))
                }
            }
            ChatMessage.Role.SYSTEM -> {
                items.add(DisplayItem.AssistantMessage(message.id, message))
            }
        }
    }
    return items
}
