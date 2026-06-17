package com.xiaomi.mimoclaw.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaomi.mimoclaw.data.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentTask: AgentTask?,
    inputText: String,
    onInputChange: (String) -> Unit,
    onExecute: () -> Unit,
    onNavigateToDetail: () -> Unit,
    onNavigateToConsole: () -> Unit,
    onNavigateToBrowser: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDebug: () -> Unit = {},
    onNavigateToQueue: () -> Unit = {},
    onNavigateToMultiAgent: () -> Unit = {},
    onNavigateToBilling: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MiMo Agent", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToDebug) {
                        Icon(Icons.Default.BugReport, "Debug")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // ── 输入区 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "告诉我要做什么",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例：打开百度搜索 MiMo Claw") },
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                        maxLines = 5
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onExecute,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = inputText.isNotBlank()
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("执行任务")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 当前任务状态 ──
            if (currentTask != null) {
                Text("当前任务", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))

                TaskStatusCard(
                    task = currentTask,
                    onClick = onNavigateToDetail
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 快捷按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToConsole,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Terminal, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("控制台")
                    }
                    OutlinedButton(
                        onClick = onNavigateToBrowser,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Public, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("浏览器")
                    }
                    OutlinedButton(
                        onClick = onNavigateToDebug,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.BugReport, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Debug")
                    }
                }

                // 平台功能按钮
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToQueue,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Queue, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("队列")
                    }
                    OutlinedButton(
                        onClick = onNavigateToMultiAgent,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Hub, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Agent")
                    }
                    OutlinedButton(
                        onClick = onNavigateToBilling,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Receipt, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("计费")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 执行控制
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (currentTask.state) {
                        TaskState.RUNNING -> {
                            Button(
                                onClick = { /* pause */ },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) { Icon(Icons.Default.Pause, null); Spacer(Modifier.width(4.dp)); Text("暂停") }
                            Button(
                                onClick = { /* cancel */ },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(4.dp)); Text("取消") }
                        }
                        TaskState.PAUSED -> {
                            Button(
                                onClick = { /* resume */ },
                                modifier = Modifier.weight(1f)
                            ) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("继续") }
                        }
                        TaskState.FAILED -> {
                            Button(
                                onClick = { /* retry */ },
                                modifier = Modifier.weight(1f)
                            ) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text("重试") }
                        }
                        else -> {}
                    }
                }
            } else {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SmartToy,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "输入指令开始执行任务",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 示例指令 ──
            Text("示例指令", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            val examples = listOf(
                "打开百度搜索 MiMo Claw",
                "打开 github.com 并截图",
                "打开百度，输入搜索内容并点击搜索按钮"
            )
            examples.forEach { example ->
                OutlinedCard(
                    onClick = { onInputChange(example) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        example,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskStatusCard(task: AgentTask, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TaskStateBadge(task.state)
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 进度条
            val completed = task.steps.count { it.state == StepState.SUCCESS }
            LinearProgressIndicator(
                progress = { if (task.steps.isEmpty()) 0f else completed.toFloat() / task.steps.size },
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "步骤 ${task.currentStepIndex + 1}/${task.steps.size} | $completed 完成",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            // 错误信息
            if (task.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    task.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun TaskStateBadge(state: TaskState) {
    val (color, text) = when (state) {
        TaskState.IDLE -> MaterialTheme.colorScheme.outline to "待执行"
        TaskState.RUNNING -> MaterialTheme.colorScheme.primary to "执行中"
        TaskState.PAUSED -> MaterialTheme.colorScheme.secondary to "已暂停"
        TaskState.FAILED -> MaterialTheme.colorScheme.error to "失败"
        TaskState.SUCCESS -> MaterialTheme.colorScheme.tertiary to "完成"
        TaskState.CANCELLED -> MaterialTheme.colorScheme.outline to "已取消"
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
