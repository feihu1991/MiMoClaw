package com.xiaomi.mimoclaw.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.xiaomi.mimoclaw.data.model.ChatMode
import com.xiaomi.mimoclaw.data.model.Conversation
import com.xiaomi.mimoclaw.ui.component.Sidebar

@Composable
fun MainScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToChat: (ChatMode, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSubscribe: () -> Unit,
    onNavigateToApiService: () -> Unit
) {
    var sidebarVisible by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var currentMode by remember { mutableStateOf(ChatMode.MIMO_CLAW) }
    var conversations by remember { mutableStateOf(listOf<Conversation>()) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Sidebar
        Sidebar(
            isVisible = sidebarVisible,
            currentMode = currentMode,
            conversations = conversations,
            isLoggedIn = isLoggedIn,
            onClose = { sidebarVisible = false },
            onModeChanged = { currentMode = it },
            onConversationClick = { conv ->
                onNavigateToChat(conv.mode, conv.id)
            },
            onNewConversation = {
                onNavigateToChat(currentMode, "new")
            },
            onDeleteConversation = { conv ->
                conversations = conversations.filter { it.id != conv.id }
            },
            onLogin = onNavigateToLogin,
            onNavigateToSubscribe = onNavigateToSubscribe,
            onNavigateToApiService = onNavigateToApiService,
            onNavigateToSettings = onNavigateToSettings
        )

        // Main content
        HomeScreen(
            onStartChat = { mode ->
                onNavigateToChat(mode, "new")
            },
            onNavigateToSubscribe = onNavigateToSubscribe,
            isLoggedIn = isLoggedIn
        )
    }

    // Toggle sidebar with menu button in top bar
    // This is handled via the top bar in a real app
}
