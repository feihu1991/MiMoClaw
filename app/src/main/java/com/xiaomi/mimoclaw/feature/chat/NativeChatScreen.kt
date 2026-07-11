package com.xiaomi.mimoclaw.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaomi.mimoclaw.feature.chat.model.ChatMessage

/** Native MiMo Chat surface. It deliberately does not embed the web console. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeChatScreen(
    viewModel: ChatViewModel,
    onClaw: () -> Unit,
    onFiles: () -> Unit,
    onProfile: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val current by viewModel.currentConversation.collectAsState()
    var input by remember { mutableStateOf("") }
    var historyOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = { historyOpen = true }) { Text("记录") } },
                title = { Text("MiMo Chat", fontWeight = FontWeight.SemiBold) },
                actions = { TextButton(onClick = { viewModel.newConversation() }) { Text("新对话", fontWeight = FontWeight.SemiBold) } }
            )
        },
        bottomBar = {
            Column(Modifier.imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("问问 MiMo…") },
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = { if (input.isNotBlank()) { viewModel.sendMessage(input); input = "" } },
                        modifier = Modifier.size(46.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = MaterialTheme.colorScheme.onPrimary) } }
                }
                NavigationBar {
                    NavigationBarItem(selected = false, onClick = onClaw, icon = { Icon(Icons.Default.Code, null) }, label = { Text("Claw") })
                    NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.Public, null) }, label = { Text("Chat") })
                    NavigationBarItem(selected = false, onClick = onFiles, icon = { Icon(Icons.Default.FolderOpen, null) }, label = { Text("文件") })
                    NavigationBarItem(selected = false, onClick = onProfile, icon = { Icon(Icons.Default.Person, null) }, label = { Text("我的") })
                }
            }
        }
    ) { padding ->
        val messages = current?.messages.orEmpty()
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("MiMo Chat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("开始一个新的对话", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(messages, key = { it.id }) { message -> NativeMessage(message) }
            }
        }
    }

    if (historyOpen) {
        ModalBottomSheet(onDismissRequest = { historyOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("对话记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                conversations.forEach { conversation ->
                    TextButton(onClick = { viewModel.selectConversation(conversation); historyOpen = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(conversation.title, modifier = Modifier.weight(1f))
                    }
                }
                TextButton(onClick = { viewModel.newConversation(); historyOpen = false }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("新对话") }
            }
        }
    }
}

@Composable
private fun NativeMessage(message: ChatMessage) {
    val user = message.role == ChatMessage.Role.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp)
        ) { Text(message.content.ifBlank { "正在回复…" }, Modifier.padding(14.dp), color = if (user) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
