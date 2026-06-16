package com.xiaomi.mimoclaw.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomi.mimoclaw.data.model.ChatMessage
import com.xiaomi.mimoclaw.data.model.ChatMode
import com.xiaomi.mimoclaw.data.model.MessageRole
import com.xiaomi.mimoclaw.ui.component.ChatBubble
import com.xiaomi.mimoclaw.ui.component.MessageInput
import com.xiaomi.mimoclaw.ui.theme.MiMoGradientEnd
import com.xiaomi.mimoclaw.ui.theme.MiMoGradientStart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatMode: ChatMode,
    messages: List<ChatMessage>,
    isLoading: Boolean = false,
    onSendMessage: (String) -> Unit,
    onBack: () -> Unit,
    onNewConversation: () -> Unit,
    onToggleSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(chatMode.displayName, fontWeight = FontWeight.SemiBold)
                        if (chatMode == ChatMode.MIMO_CLAW) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary) {
                                Text(
                                    "New Upgrade",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onNewConversation) { Icon(Icons.Default.Add, "新对话") }
                    IconButton(onClick = onToggleSidebar) { Icon(Icons.Default.Menu, "侧边栏") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Column {
                // 工具栏
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { },
                        label = { Text("消息通道", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Email, null, modifier = Modifier.size(14.dp)) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    )
                    AssistChip(
                        onClick = { },
                        label = { Text("技能", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Extension, null, modifier = Modifier.size(14.dp)) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    )
                    AssistChip(
                        onClick = { },
                        label = { Text("WPS 办公", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Description, null, modifier = Modifier.size(14.dp)) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    )
                }

                // 模型选择
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("MiMo-V2.5-Pro", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                MessageInput(onSend = onSendMessage, isLoading = isLoading, onAttach = { })
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            EmptyChatState(
                chatMode = chatMode,
                onStartChat = { onSendMessage(it) },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(items = messages, key = { it.id }) { message ->
                    ChatBubble(message = message)
                }

                if (isLoading && messages.lastOrNull()?.role == MessageRole.USER) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(MiMoGradientStart, MiMoGradientEnd))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.SmartToy, "AI", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatState(chatMode: ChatMode, onStartChat: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(MiMoGradientStart, MiMoGradientEnd))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.SmartToy, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("开始对话", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "输入消息或点击下方建议开始",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        val suggestions = listOf(
            "🌐 网页设计" to "创建一个响应式着陆页",
            "📝 脚本与旁白" to "写一个短视频脚本",
            "📊 销售数据分析" to "分析 Q2 销售趋势",
            "📰 每日新闻摘要" to "总结今日热点"
        )

        suggestions.forEach { (label, _) ->
            OutlinedButton(
                onClick = { onStartChat(label) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            }
        }
    }
}
