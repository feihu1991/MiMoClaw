package com.xiaomi.mimoclaw.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaomi.mimoclaw.data.local.PreferencesManager
import com.xiaomi.mimoclaw.data.model.*
import com.xiaomi.mimoclaw.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    // Auth state
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    // Theme
    val themeMode: StateFlow<ThemeMode> = preferencesManager.themeMode
        .map { ThemeMode.valueOf(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val fontSize: StateFlow<FontSize> = preferencesManager.fontSize
        .map { FontSize.valueOf(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FontSize.MEDIUM)

    // Model
    val selectedModel: StateFlow<String> = preferencesManager.selectedModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MiMo-V2.5-Pro")

    // Chat state
    private val _currentMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val currentMessages: StateFlow<List<ChatMessage>> = _currentMessages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)

    // Conversations
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _currentMode = MutableStateFlow(ChatMode.MIMO_CLAW)
    val currentMode: StateFlow<ChatMode> = _currentMode.asStateFlow()

    init {
        // Check for saved auth token
        viewModelScope.launch {
            preferencesManager.authToken.collect { token ->
                _isLoggedIn.value = token != null
            }
        }
    }

    fun setChatMode(mode: ChatMode) {
        _currentMode.value = mode
        _conversations.value = chatRepository.getConversations(mode)
    }

    fun createNewConversation(mode: ChatMode) {
        val conv = chatRepository.createConversation(mode)
        _currentConversationId.value = conv.id
        _currentMessages.value = emptyList()
        _conversations.value = chatRepository.getConversations(mode)
    }

    fun loadConversation(conversationId: String) {
        _currentConversationId.value = conversationId
        _currentMessages.value = chatRepository.getMessages(conversationId)
    }

    fun sendMessage(content: String, attachments: List<Attachment> = emptyList()) {
        val convId = _currentConversationId.value ?: run {
            val conv = chatRepository.createConversation(_currentMode.value)
            _currentConversationId.value = conv.id
            conv.id
        }

        viewModelScope.launch {
            _isLoading.value = true
            chatRepository.sendMessage(convId, content, attachments).collect { message ->
                _currentMessages.value = chatRepository.getMessages(convId)
            }
            _isLoading.value = false
            _conversations.value = chatRepository.getConversations(_currentMode.value)
        }
    }

    fun deleteConversation(conversation: Conversation) {
        chatRepository.deleteConversation(conversation.id)
        if (_currentConversationId.value == conversation.id) {
            _currentConversationId.value = null
            _currentMessages.value = emptyList()
        }
        _conversations.value = chatRepository.getConversations(_currentMode.value)
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesManager.saveThemeMode(mode.name)
        }
    }

    fun updateFontSize(size: FontSize) {
        viewModelScope.launch {
            preferencesManager.saveFontSize(size.name)
        }
    }

    fun updateSelectedModel(model: String) {
        viewModelScope.launch {
            preferencesManager.saveSelectedModel(model)
        }
    }

    fun login(token: String, user: User) {
        viewModelScope.launch {
            preferencesManager.saveToken(token)
            preferencesManager.saveUserId(user.userId)
            _isLoggedIn.value = true
            _user.value = user
        }
    }

    fun logout() {
        viewModelScope.launch {
            preferencesManager.clearAll()
            _isLoggedIn.value = false
            _user.value = null
            _currentConversationId.value = null
            _currentMessages.value = emptyList()
        }
    }
}
