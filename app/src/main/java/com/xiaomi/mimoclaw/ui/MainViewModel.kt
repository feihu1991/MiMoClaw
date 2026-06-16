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

    // Auth
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    // Theme
    val themeMode = preferencesManager.themeMode
        .map { ThemeMode.valueOf(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    // Model
    val selectedModel = preferencesManager.selectedModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MiMo-V2.5-Pro")

    // Current conversation
    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _currentMode = MutableStateFlow(ChatMode.MIMO_CLAW)
    val currentMode: StateFlow<ChatMode> = _currentMode.asStateFlow()

    // Messages for current conversation
    val currentMessages: StateFlow<List<ChatMessage>> = _currentConversationId
        .filterNotNull()
        .flatMapLatest { id -> chatRepository.getMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Conversations list
    val conversations: StateFlow<List<Conversation>> = _currentMode
        .flatMapLatest { mode -> chatRepository.getConversations(mode) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.authToken.collect { token ->
                _isLoggedIn.value = token != null
            }
        }
    }

    fun setChatMode(mode: ChatMode) {
        _currentMode.value = mode
    }

    fun createNewConversation() {
        viewModelScope.launch {
            val conv = chatRepository.createConversation(_currentMode.value)
            _currentConversationId.value = conv.id
        }
    }

    fun loadConversation(id: String) {
        _currentConversationId.value = id
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conversation.id)
            if (_currentConversationId.value == conversation.id) {
                _currentConversationId.value = null
            }
        }
    }

    fun sendMessage(content: String) {
        val convId = _currentConversationId.value
        if (convId == null) {
            // Create conversation first
            viewModelScope.launch {
                val conv = chatRepository.createConversation(_currentMode.value)
                _currentConversationId.value = conv.id
                doSendMessage(conv.id, content)
            }
        } else {
            viewModelScope.launch {
                doSendMessage(convId, content)
            }
        }
    }

    private suspend fun doSendMessage(conversationId: String, content: String) {
        _isLoading.value = true
        chatRepository.sendMessage(conversationId, content).collect { }
        _isLoading.value = false
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
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesManager.saveThemeMode(mode.name) }
    }

    fun updateSelectedModel(model: String) {
        viewModelScope.launch { preferencesManager.saveSelectedModel(model) }
    }
}
