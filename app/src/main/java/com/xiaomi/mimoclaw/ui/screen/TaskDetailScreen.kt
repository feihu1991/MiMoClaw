package com.xiaomi.mimoclaw.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun TaskDetailScreen(
    task: AgentTask?,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("任务详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (task == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无任务")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 任务信息
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(task.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (task.description.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(task.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            InfoChip("状态", stateText(task.state))
                            InfoChip("步骤", "${task.steps.size}")
                            InfoChip("重试", "${task.retryCount}/${task.maxRetries}")
                        }
                    }
                }
            }

            // 步骤列表
            item {
                Text("执行步骤", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp))
            }

            itemsIndexed(task.steps) { index, step ->
                StepCard(index = index, step = step, isActive = index == task.currentStepIndex)
            }
        }
    }
}

@Composable
private fun StepCard(index: Int, step: TaskStep, isActive: Boolean) {
    val borderColor = when (step.state) {
        StepState.SUCCESS -> MaterialTheme.colorScheme.tertiary
        StepState.FAILED -> MaterialTheme.colorScheme.error
        StepState.RUNNING -> MaterialTheme.colorScheme.primary
        StepState.RETRYING -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 步骤编号
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                when (step.state) {
                    StepState.SUCCESS -> Icon(Icons.Default.CheckCircle, null,
                        tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                    StepState.FAILED -> Icon(Icons.Default.Error, null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                    StepState.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    else -> Text("${index + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            step.type.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(step.description, fontWeight = FontWeight.Medium)
                }

                if (step.selector != null) {
                    Text("选择器: ${step.selector}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
                if (step.value != null) {
                    Text("值: ${step.value}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline)
                }
                if (step.error != null) {
                    Text(step.error, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                if (step.result != null) {
                    Text("结果: ${step.result.take(100)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

private fun stateText(state: TaskState): String = when (state) {
    TaskState.IDLE -> "待执行"
    TaskState.RUNNING -> "执行中"
    TaskState.PAUSED -> "已暂停"
    TaskState.FAILED -> "失败"
    TaskState.SUCCESS -> "完成"
    TaskState.CANCELLED -> "已取消"
}
