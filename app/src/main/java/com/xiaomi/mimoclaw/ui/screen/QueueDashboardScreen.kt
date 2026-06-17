package com.xiaomi.mimoclaw.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.xiaomi.mimoclaw.platform.queue.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueDashboardScreen(
    tasks: List<QueuedTask>,
    stats: QueueStats,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("任务队列") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 统计卡片
            item {
                StatsCard(stats)
            }
            
            // 任务列表
            if (tasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Queue,
                                null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("暂无任务", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            } else {
                items(tasks) { task ->
                    QueueTaskCard(
                        task = task,
                        onPause = { onPause(task.id) },
                        onResume = { onResume(task.id) },
                        onCancel = { onCancel(task.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsCard(stats: QueueStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("队列状态", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("总计", stats.total.toString(), MaterialTheme.colorScheme.outline)
                StatItem("等待", stats.pending.toString(), MaterialTheme.colorScheme.primary)
                StatItem("执行中", stats.running.toString(), MaterialTheme.colorScheme.tertiary)
                StatItem("完成", stats.completed.toString(), MaterialTheme.colorScheme.tertiary)
                StatItem("失败", stats.failed.toString(), MaterialTheme.colorScheme.error)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 进度条
            if (stats.total > 0) {
                LinearProgressIndicator(
                    progress = { stats.completed.toFloat() / stats.total },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${stats.completed}/${stats.total} 完成",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun QueueTaskCard(
    task: QueuedTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val statusColor = when (task.status) {
        QueueStatus.PENDING -> MaterialTheme.colorScheme.primary
        QueueStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
        QueueStatus.PAUSED -> MaterialTheme.colorScheme.secondary
        QueueStatus.RETRY -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        QueueStatus.FAILED -> MaterialTheme.colorScheme.error
        QueueStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
        QueueStatus.CANCELLED -> MaterialTheme.colorScheme.outline
    }
    
    val statusText = when (task.status) {
        QueueStatus.PENDING -> "等待中"
        QueueStatus.RUNNING -> "执行中"
        QueueStatus.PAUSED -> "已暂停"
        QueueStatus.RETRY -> "重试中"
        QueueStatus.FAILED -> "失败"
        QueueStatus.SUCCESS -> "完成"
        QueueStatus.CANCELLED -> "已取消"
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 状态指示
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(task.task.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 进度
            val completed = task.task.steps.count { it.state == com.xiaomi.mimoclaw.data.model.StepState.SUCCESS }
            LinearProgressIndicator(
                progress = { if (task.task.steps.isEmpty()) 0f else completed.toFloat() / task.task.steps.size },
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "$completed/${task.task.steps.size} 步骤 | 优先级: ${task.priority} | 重试: ${task.retryCount}/${task.maxRetries}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            
            // 错误信息
            if (task.error != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(task.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            
            // 操作按钮
            if (task.status == QueueStatus.RUNNING || task.status == QueueStatus.PAUSED) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (task.status == QueueStatus.RUNNING) {
                        OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Pause, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("暂停")
                        }
                    } else {
                        OutlinedButton(onClick = onResume, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("继续")
                        }
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("取消")
                    }
                }
            }
        }
    }
}
