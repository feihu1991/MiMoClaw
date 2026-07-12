package com.xiaomi.mimoclaw.feature.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomi.mimoclaw.core.network.ConnectionState
import com.xiaomi.mimoclaw.feature.chat.model.ChatMessage
import com.xiaomi.mimoclaw.feature.chat.model.Conversation
import com.xiaomi.mimoclaw.feature.chat.model.DisplayItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToBrowser: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val conversations by viewModel.conversations.collectAsState()
    val currentConversation by viewModel.currentConversation.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val displayItems by viewModel.displayItems.collectAsState()
    var inputText by remember { mutableStateOf("") }

    fun closeDrawerAnd(action: () -> Unit) {
        scope.launch {
            drawerState.close()
            action()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawer(
                conversations = conversations,
                selectedConversationId = currentConversation?.id,
                connectionState = connectionState,
                onNewConversation = {
                    viewModel.newConversation()
                    inputText = ""
                    closeDrawerAnd { }
                },
                onSelectConversation = { conversation ->
                    viewModel.selectConversation(conversation)
                    inputText = ""
                    closeDrawerAnd { }
                },
                onDeleteConversation = viewModel::deleteConversation,
                onBrowser = { closeDrawerAnd(onNavigateToBrowser) },
                onFiles = { closeDrawerAnd(onNavigateToFiles) },
                onSettings = { closeDrawerAnd(onNavigateToSettings) }
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                ConversationTopBar(
                    onHistoryClick = { scope.launch { drawerState.open() } },
                    onNewClick = {
                        viewModel.newConversation()
                        inputText = ""
                    }
                )
            },
            bottomBar = {
                Column {
                    ChatInputBar(
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        onStop = viewModel::abortChat,
                        isStreaming = isStreaming,
                        model = "MiMo Claw"
                    )
                    ClawBottomNavigation(
                        onChat = onNavigateToBrowser,
                        onFiles = onNavigateToFiles,
                        onProfile = onNavigateToSettings
                    )
                }
            }
        ) { padding ->
            if (displayItems.isEmpty()) {
                EmptyConversation(
                    onSuggestionSelected = { inputText = it },
                    modifier = Modifier.padding(padding)
                )
            } else {
                ConversationContent(
                    displayItems = displayItems,
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationTopBar(
    onHistoryClick: () -> Unit,
    onNewClick: () -> Unit
) {
    TopAppBar(
        windowInsets = WindowInsets.statusBars,
        navigationIcon = {
            TextButton(onClick = onHistoryClick) { Text("记录") }
        },
        title = {
            Text("MiMo Claw", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        },
        actions = {
            TextButton(onClick = onNewClick) { Text("新对话", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.width(4.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun ClawBottomNavigation(
    onChat: () -> Unit,
    onFiles: () -> Unit,
    onProfile: () -> Unit
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = true,
            onClick = {},
            icon = { Icon(Icons.Default.Code, null) },
            label = { Text("Claw") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onChat,
            icon = { Icon(Icons.Default.Public, null) },
            label = { Text("Chat") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onFiles,
            icon = { Icon(Icons.Default.FolderOpen, null) },
            label = { Text("文件") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onProfile,
            icon = { Icon(Icons.Default.Settings, null) },
            label = { Text("我的") }
        )
    }
}

@Composable
private fun StatusDot(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.CONNECTED -> Color(0xFF36A269)
        ConnectionState.CONNECTING -> Color(0xFFD59A31)
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.error
    }
    val alpha by animateFloatAsState(
        targetValue = if (state == ConnectionState.CONNECTING) 0.45f else 1f,
        label = "connection_dot"
    )
    Box(
        Modifier
            .size(8.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * 对话内容区域（使用 displayItems + reverseLayout）
 */
@Composable
private fun ConversationContent(
    displayItems: List<DisplayItem>,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 自动滚动到底部（reverseLayout=true 时 index 0 是最新消息）
    val lastItemLength = displayItems.lastOrNull()?.id?.length ?: 0
    LaunchedEffect(displayItems.size, lastItemLength) {
        if (displayItems.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // 滚动按钮显示条件
    val showScrollButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2 ||
                (listState.firstVisibleItemIndex == 1 && listState.firstVisibleItemScrollOffset > 200)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 消息列表（reverseLayout=true 实现 inverted 效果）
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = true,
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = displayItems,
                key = { it.id }
            ) { item ->
                DisplayItemRow(item, viewModel)
            }
        }

        // 滚动按钮
        if (showScrollButton) {
            Surface(
                onClick = { scope.launch { listState.scrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = "回到底部",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * 显示项渲染分发
 */
@Composable
private fun DisplayItemRow(item: DisplayItem, viewModel: ChatViewModel) {
    when (item) {
        is DisplayItem.UserMessage -> UserBubble(item.message)
        is DisplayItem.AssistantMessage -> AssistantBubble(item.message)
        is DisplayItem.ToolGroup -> ToolGroupCard(
            tools = item.tools,
            isRunning = item.isRunning,
            isCollapsed = viewModel.isGroupCollapsed(item.id),
            onToggle = { viewModel.toggleGroup(item.id) }
        )
        is DisplayItem.ThinkingBlock -> {
            // 思考块已经在 AssistantBubble 中处理，这里跳过
            // 如果需要独立显示，可以添加 ThinkingBlockCard
        }
    }
}

@Composable
private fun EmptyConversation(
    onSuggestionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = remember {
        listOf(
            Suggestion(Icons.Default.Code, "分析代码", "帮我分析这段代码并指出潜在问题"),
            Suggestion(Icons.Default.Description, "整理内容", "帮我把这份内容整理成清晰的要点"),
            Suggestion(Icons.Default.AutoAwesome, "开始创作", "帮我从零开始构思一个可执行方案")
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(46.dp),
            shape = RoundedCornerShape(15.dp),
            color = MaterialTheme.colorScheme.inverseSurface
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "M",
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "今天想一起做什么？",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "提问、写作、分析代码，或把一个模糊想法变成行动。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(30.dp))
        suggestions.forEach { suggestion ->
            SuggestionRow(suggestion) { onSuggestionSelected(suggestion.prompt) }
            Spacer(Modifier.height(10.dp))
        }
    }
}

private data class Suggestion(
    val icon: ImageVector,
    val title: String,
    val prompt: String
)

@Composable
private fun SuggestionRow(suggestion: Suggestion, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                suggestion.icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(13.dp))
            Text(
                suggestion.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.ArrowUpward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ConversationDrawer(
    conversations: List<Conversation>,
    selectedConversationId: String?,
    connectionState: ConnectionState,
    onNewConversation: () -> Unit,
    onSelectConversation: (Conversation) -> Unit,
    onDeleteConversation: (Conversation) -> Unit,
    onBrowser: () -> Unit,
    onFiles: () -> Unit,
    onSettings: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.inverseSurface
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "M",
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column {
                    Text(
                        "MiMo Claw",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        when (connectionState) {
                            ConnectionState.CONNECTED -> "云端 Agent 已连接"
                            ConnectionState.CONNECTING -> "正在连接云端 Agent"
                            ConnectionState.DISCONNECTED -> "云端 Agent 未连接"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = onNewConversation,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("新建对话", fontWeight = FontWeight.Medium)
            }
        }

        Text(
            "最近",
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (conversations.isEmpty()) {
            Text(
                "还没有对话",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationDrawerItem(
                        conversation = conversation,
                        selected = conversation.id == selectedConversationId,
                        onClick = { onSelectConversation(conversation) },
                        onDelete = { onDeleteConversation(conversation) }
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ConversationDrawerItem(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        NavigationDrawerItem(
            label = {
                Text(
                    conversation.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            selected = selected,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unselectedContainerColor = Color.Transparent
            ),
            badge = {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = "对话选项",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            DropdownMenuItem(
                text = { Text("删除对话") },
                leadingIcon = {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
        }
    }
}
