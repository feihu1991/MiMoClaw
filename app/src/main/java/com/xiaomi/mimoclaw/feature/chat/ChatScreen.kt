package com.xiaomi.mimoclaw.feature.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
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
                SessionTopBar(
                    conversationTitle = currentConversation?.title ?: "新对话",
                    connectionState = connectionState,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNewClick = {
                        viewModel.newConversation()
                        inputText = ""
                    }
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AgentSessionStatus(
                        connectionState = connectionState,
                        isStreaming = isStreaming
                    )
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
                        model = currentConversation?.model ?: "mimo-v2.5-pro"
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
private fun SessionTopBar(
    conversationTitle: String,
    connectionState: ConnectionState,
    onMenuClick: () -> Unit,
    onNewClick: () -> Unit
) {
    TopAppBar(
        windowInsets = WindowInsets.statusBars,
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "打开会话列表")
            }
        },
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MiMo Claw",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "  /  ",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = conversationTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(connectionState)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = connectionText(connectionState),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onNewClick) {
                Icon(Icons.Default.Add, contentDescription = "新建对话")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun AgentSessionStatus(
    connectionState: ConnectionState,
    isStreaming: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 760.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(connectionState)
            Spacer(Modifier.width(7.dp))
            Text(
                text = if (isStreaming) "Agent 正在执行" else connectionText(connectionState),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            ) {
                Text(
                    text = "本地工作区",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusDot(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.CONNECTED -> Color(0xFF36A269)
        ConnectionState.CONNECTING -> Color(0xFFD59A31)
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.error
    }
    val dotAlpha by animateFloatAsState(
        targetValue = if (state == ConnectionState.CONNECTING) 0.45f else 1f,
        label = "connection_dot"
    )
    Box(
        Modifier
            .size(8.dp)
            .alpha(dotAlpha)
            .clip(CircleShape)
            .background(color)
    )
}

private fun connectionText(state: ConnectionState): String = when (state) {
    ConnectionState.CONNECTED -> "Agent 已连接"
    ConnectionState.CONNECTING -> "正在连接 Agent"
    ConnectionState.DISCONNECTED -> "Agent 未连接"
}

@Composable
private fun ConversationContent(
    displayItems: List<DisplayItem>,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lastContentLength = when (val last = displayItems.lastOrNull()) {
        is DisplayItem.AssistantMessage -> last.message.content.length
        is DisplayItem.UserMessage -> last.message.content.length
        is DisplayItem.ToolGroup -> last.tools.size
        is DisplayItem.ThinkingBlock -> last.text.length
        null -> 0
    }

    LaunchedEffect(displayItems.size, lastContentLength) {
        if (displayItems.isNotEmpty()) listState.scrollToItem(0)
    }

    val showScrollButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2 ||
                (listState.firstVisibleItemIndex == 1 && listState.firstVisibleItemScrollOffset > 200)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = true,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items = displayItems, key = { it.id }) { item ->
                DisplayItemRow(item, viewModel)
            }
        }

        if (showScrollButton) {
            Surface(
                onClick = { scope.launch { listState.scrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .size(38.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = "回到底部",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

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
        is DisplayItem.ThinkingBlock -> Unit
    }
}

@Composable
private fun EmptyConversation(
    onSuggestionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = remember {
        listOf(
            Suggestion(Icons.Default.Code, "修改项目代码", "读取当前项目，帮我修改代码并展示改动"),
            Suggestion(Icons.Default.Description, "检查本次改动", "检查当前修改，指出风险并给出改进建议"),
            Suggestion(Icons.Default.AutoAwesome, "提交到 Git", "整理本次修改，生成提交信息并准备提交到 Git")
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp)) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
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
            Spacer(Modifier.height(22.dp))
            Text(
                "今天想改什么？",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "读取文件、修改代码、查看差异并提交 Git。构建、打包和部署不在本地 Agent 范围内。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp
            )
            Spacer(Modifier.height(28.dp))
            suggestions.forEach { suggestion ->
                SuggestionRow(suggestion) { onSuggestionSelected(suggestion.prompt) }
                Spacer(Modifier.height(10.dp))
            }
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 14.dp, top = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(12.dp),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(connectionState)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            connectionText(connectionState),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = onNewConversation,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
            "最近会话",
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (conversations.isEmpty()) {
            Text(
                "还没有对话",
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
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
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            DrawerDestination(Icons.Default.Public, "MiMo Chat", onBrowser)
            DrawerDestination(Icons.Default.FolderOpen, "文件工作区", onFiles)
            DrawerDestination(Icons.Default.Settings, "设置", onSettings)
        }
    }
}

@Composable
private fun DrawerDestination(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        shape = RoundedCornerShape(12.dp),
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent
        )
    )
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
