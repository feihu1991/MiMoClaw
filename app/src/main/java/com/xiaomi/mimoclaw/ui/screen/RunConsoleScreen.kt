package com.xiaomi.mimoclaw.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomi.mimoclaw.data.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunConsoleScreen(
    task: AgentTask?,
    logs: List<AgentLog>,
    logText: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("运行控制台") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 控制按钮
                    if (task != null) {
                        when (task.state) {
                            TaskState.RUNNING -> {
                                IconButton(onClick = onPause) {
                                    Icon(Icons.Default.Pause, "暂停")
                                }
                                IconButton(onClick = onCancel) {
                                    Icon(Icons.Default.Close, "取消")
                                }
                            }
                            TaskState.PAUSED -> {
                                IconButton(onClick = onResume) {
                                    Icon(Icons.Default.PlayArrow, "继续")
                                }
                            }
                            TaskState.FAILED -> {
                                IconButton(onClick = onRetry) {
                                    Icon(Icons.Default.Refresh, "重试")
                                }
                            }
                            else -> {}
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 状态栏
            if (task != null) {
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val (stateColor, stateText) = when (task.state) {
                            TaskState.IDLE -> MaterialTheme.colorScheme.outline to "待执行"
                            TaskState.RUNNING -> MaterialTheme.colorScheme.primary to "执行中"
                            TaskState.PAUSED -> MaterialTheme.colorScheme.secondary to "已暂停"
                            TaskState.FAILED -> MaterialTheme.colorScheme.error to "失败"
                            TaskState.SUCCESS -> MaterialTheme.colorScheme.tertiary to "完成"
                            TaskState.CANCELLED -> MaterialTheme.colorScheme.outline to "已取消"
                        }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(stateColor, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stateText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "步骤 ${task.currentStepIndex + 1}/${task.steps.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // 日志区域
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs) { log ->
                    LogLine(log)
                }

                if (logs.isEmpty()) {
                    item {
                        Text(
                            "等待执行...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLine(log: AgentLog) {
    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        .format(java.util.Date(log.timestamp))

    val levelColor = when (log.level) {
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.WARN -> MaterialTheme.colorScheme.secondary
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.DEBUG -> MaterialTheme.colorScheme.outline
    }

    val statusIcon = when (log.status) {
        LogStatus.STARTED -> "▶"
        LogStatus.RUNNING -> "⟳"
        LogStatus.SUCCESS -> "✓"
        LogStatus.FAILED -> "✗"
        LogStatus.RETRYING -> "↻"
        LogStatus.SKIPPED -> "⊘"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            time,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            statusIcon,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            log.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = levelColor,
            fontSize = 12.sp
        )
    }
}
