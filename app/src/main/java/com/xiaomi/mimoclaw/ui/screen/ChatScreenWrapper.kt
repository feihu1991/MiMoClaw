package com.xiaomi.mimoclaw.ui.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaomi.mimoclaw.data.model.ChatMode
import com.xiaomi.mimoclaw.ui.MainViewModel
import com.xiaomi.mimoclaw.ui.component.Sidebar

@Composable
fun ChatScreenWrapper(
    modeName: String,
    conversationId: String,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSubscribe: () -> Unit,
    onNavigateToApiService: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val chatMode = try { ChatMode.valueOf(modeName) } catch (_: Exception) { ChatMode.MIMO_CLAW }
    var sidebarVisible by remember { mutableStateOf(false) }

    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val currentConvId by viewModel.currentConversationId.collectAsState()

    // Initialize
    LaunchedEffect(Unit) {
        viewModel.setChatMode(chatMode)
        if (conversationId != "new") {
            viewModel.loadConversation(conversationId)
        } else {
            viewModel.createNewConversation()
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Sidebar(
            isVisible = sidebarVisible,
            currentMode = currentMode,
            conversations = conversations,
            isLoggedIn = isLoggedIn,
            onClose = { sidebarVisible = false },
            onModeChanged = { viewModel.setChatMode(it) },
            onConversationClick = { conv ->
                viewModel.loadConversation(conv.id)
                sidebarVisible = false
            },
            onNewConversation = {
                viewModel.createNewConversation()
                sidebarVisible = false
            },
            onDeleteConversation = { viewModel.deleteConversation(it) },
            onLogin = onNavigateToLogin,
            onNavigateToSubscribe = onNavigateToSubscribe,
            onNavigateToApiService = onNavigateToApiService,
            onNavigateToSettings = onNavigateToSettings
        )

        ChatScreen(
            chatMode = currentMode,
            messages = messages,
            isLoading = isLoading,
            onSendMessage = { viewModel.sendMessage(it) },
            onBack = onBack,
            onNewConversation = { viewModel.createNewConversation() },
            onToggleSidebar = { sidebarVisible = !sidebarVisible }
        )
    }
}
