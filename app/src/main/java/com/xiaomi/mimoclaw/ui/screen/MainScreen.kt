package com.xiaomi.mimoclaw.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaomi.mimoclaw.data.model.ChatMode
import com.xiaomi.mimoclaw.ui.MainViewModel
import com.xiaomi.mimoclaw.ui.component.Sidebar

@Composable
fun MainScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToChat: (ChatMode, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSubscribe: () -> Unit,
    onNavigateToApiService: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    var sidebarVisible by remember { mutableStateOf(false) }
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val conversations by viewModel.conversations.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        Sidebar(
            isVisible = sidebarVisible,
            currentMode = currentMode,
            conversations = conversations,
            isLoggedIn = isLoggedIn,
            onClose = { sidebarVisible = false },
            onModeChanged = { viewModel.setChatMode(it) },
            onConversationClick = { conv ->
                onNavigateToChat(conv.mode, conv.id)
            },
            onNewConversation = {
                onNavigateToChat(currentMode, "new")
            },
            onDeleteConversation = { viewModel.deleteConversation(it) },
            onLogin = onNavigateToLogin,
            onNavigateToSubscribe = onNavigateToSubscribe,
            onNavigateToApiService = onNavigateToApiService,
            onNavigateToSettings = onNavigateToSettings
        )

        HomeScreen(
            onStartChat = { mode -> onNavigateToChat(mode, "new") },
            onNavigateToSubscribe = onNavigateToSubscribe,
            onNavigateToLogin = onNavigateToLogin,
            isLoggedIn = isLoggedIn
        )
    }
}
