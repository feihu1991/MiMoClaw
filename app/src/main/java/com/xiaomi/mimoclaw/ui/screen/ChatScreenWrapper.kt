package com.xiaomi.mimoclaw.ui.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.xiaomi.mimoclaw.data.model.ChatMode
import com.xiaomi.mimoclaw.data.model.ChatMessage
import com.xiaomi.mimoclaw.data.model.Conversation
import com.xiaomi.mimoclaw.ui.component.Sidebar

@Composable
fun ChatScreenWrapper(
    modeName: String,
    conversationId: String,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSubscribe: () -> Unit,
    onNavigateToApiService: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val chatMode = try {
        ChatMode.valueOf(modeName)
    } catch (_: Exception) {
        ChatMode.MIMO_CLAW
    }

    var sidebarVisible by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var currentMode by remember { mutableStateOf(chatMode) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isLoading by remember { mutableStateOf(false) }
    var conversations by remember { mutableStateOf(listOf<Conversation>()) }
    var currentConversationId by remember { mutableStateOf(conversationId) }

    Row(modifier = Modifier.fillMaxSize()) {
        Sidebar(
            isVisible = sidebarVisible,
            currentMode = currentMode,
            conversations = conversations,
            isLoggedIn = isLoggedIn,
            onClose = { sidebarVisible = false },
            onModeChanged = { currentMode = it },
            onConversationClick = { conv ->
                currentConversationId = conv.id
                // Load messages for this conversation
                sidebarVisible = false
            },
            onNewConversation = {
                currentConversationId = "new"
                messages = emptyList()
                sidebarVisible = false
            },
            onDeleteConversation = { conv ->
                conversations = conversations.filter { it.id != conv.id }
            },
            onLogin = onNavigateToLogin,
            onNavigateToSubscribe = onNavigateToSubscribe,
            onNavigateToApiService = onNavigateToApiService,
            onNavigateToSettings = onNavigateToSettings
        )

        ChatScreen(
            chatMode = currentMode,
            messages = messages,
            isLoading = isLoading,
            onSendMessage = { content ->
                // Add user message
                val userMsg = ChatMessage(
                    role = com.xiaomi.mimoclaw.data.model.MessageRole.USER,
                    content = content
                )
                messages = messages + userMsg

                // Simulate AI response (replace with actual API call)
                isLoading = true
                // TODO: Call ViewModel/Repository to send message
                isLoading = false
            },
            onBack = onBack,
            onNewConversation = {
                currentConversationId = "new"
                messages = emptyList()
            },
            onToggleSidebar = { sidebarVisible = !sidebarVisible }
        )
    }
}
