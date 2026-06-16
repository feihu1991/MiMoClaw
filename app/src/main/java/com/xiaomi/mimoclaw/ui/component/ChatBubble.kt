package com.xiaomi.mimoclaw.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.xiaomi.mimoclaw.data.model.ChatMessage
import com.xiaomi.mimoclaw.data.model.MessageRole
import com.xiaomi.mimoclaw.ui.theme.*

@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val clipboardManager = LocalClipboardManager.current
    var showCopyToast by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
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
        }

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.animateContentSize()
            ) {
                if (isUser) {
                    // User messages: plain text
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                } else {
                    // Assistant messages: Markdown rendering
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        if (message.content.isEmpty() && message.isStreaming) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Thinking…", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            MarkdownText(
                                markdown = message.content,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant.hashCode()
                            )
                        }
                    }
                }
            }

            // Action buttons for assistant messages
            if (!isUser && message.content.isNotEmpty() && !message.isStreaming) {
                Row(
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.content))
                            showCopyToast = true
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            if (message.isStreaming && message.content.isNotEmpty()) {
                Text(
                    text = "typing…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp)
                )
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, "User", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
