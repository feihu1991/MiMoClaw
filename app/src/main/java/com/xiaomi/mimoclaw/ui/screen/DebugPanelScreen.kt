package com.xiaomi.mimoclaw.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
fun DebugPanelScreen(
    task: AgentTask?,
    loopState: LoopState,
    observations: List<Observation>,
    checkpoint: TaskCheckpoint?,
    logs: List<AgentLog>,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agent Loop Debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Loop Phase 状态 ──
            item {
                LoopPhaseCard(loopState)
            }

            // ── 当前任务状态 ──
            if (task != null) {
                item {
                    TaskOverviewCard(task, checkpoint)
                }
            }

            // ── Observation 列表 ──
            if (observations.isNotEmpty()) {
                item {
                    Text("Observations", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
                }
                itemsIndexed(observations.reversed().take(10)) { index, obs ->
                    ObservationCard(obs, index == 0)
                }
            }

            // ── Error Classification ──
            val failure = loopState.failureClassification
            if (failure != null) {
                item {
                    FailureClassificationCard(failure)
                }
            }

            // ── 修复记录 ──
            if (loopState.visionFallbackUsed || loopState.llmRepairUsed) {
                item {
                    RepairStatusCard(loopState)
                }
            }

            // ── 执行时间线 ──
            item {
                Text("执行时间线", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
            }
            items(logs.takeLast(20).reversed()) { log ->
                TimelineLogItem(log)
            }
        }
    }
}

@Composable
private fun LoopPhaseCard(state: LoopState) {
    val (color, text, icon) = when (state.phase) {
        LoopPhase.IDLE -> Triple(MaterialTheme.colorScheme.outline, "空闲", Icons.Default.PauseCircle)
        LoopPhase.OBSERVING -> Triple(MaterialTheme.colorScheme.primary, "观察中", Icons.Default.Visibility)
        LoopPhase.DECIDING -> Triple(MaterialTheme.colorScheme.secondary, "决策中", Icons.Default.Psychology)
        LoopPhase.ACTING -> Triple(MaterialTheme.colorScheme.tertiary, "执行中", Icons.Default.PlayArrow)
        LoopPhase.VERIFYING -> Triple(MaterialTheme.colorScheme.primary, "验证中", Icons.Default.Verified)
        LoopPhase.REPAIRING -> Triple(MaterialTheme.colorScheme.error, "修复中", Icons.Default.Build)
        LoopPhase.COMPLETED -> Triple(MaterialTheme.colorScheme.tertiary, "完成", Icons.Default.CheckCircle)
        LoopPhase.FAILED -> Triple(MaterialTheme.colorScheme.error, "失败", Icons.Default.Error)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Agent Loop Phase", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
                Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (state.currentStepIndex >= 0) {
                Text("Step ${state.currentStepIndex + 1}", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun TaskOverviewCard(task: AgentTask, checkpoint: TaskCheckpoint?) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(task.name, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            val completed = task.steps.count { it.state == StepState.SUCCESS }
            LinearProgressIndicator(
                progress = { if (task.steps.isEmpty()) 0f else completed.toFloat() / task.steps.size },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("$completed/${task.steps.size} 完成", style = MaterialTheme.typography.labelSmall)
                if (checkpoint != null) {
                    Text("Checkpoint: Step ${checkpoint.currentStep}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            // 步骤状态流
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                task.steps.forEach { step ->
                    val stepColor = when (step.state) {
                        StepState.SUCCESS -> MaterialTheme.colorScheme.tertiary
                        StepState.FAILED -> MaterialTheme.colorScheme.error
                        StepState.RUNNING -> MaterialTheme.colorScheme.primary
                        StepState.RETRYING -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(stepColor, RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun ObservationCard(obs: Observation, isLatest: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLatest) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (obs.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    null,
                    tint = if (obs.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Step: ${obs.stepId.take(8)}", style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.weight(1f))
                if (isLatest) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary) {
                        Text("最新", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("URL: ${obs.currentUrl}", style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Text("DOM: ${if (obs.domExists) "存在" else "不存在"}", style = MaterialTheme.typography.bodySmall)
            if (obs.error != null) {
                Text("Error: ${obs.error}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FailureClassificationCard(failure: FailureClassification) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BugReport, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("错误分类", fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("类型: ${failure.type.name}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            Text("消息: ${failure.message}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (failure.shouldRetry) InfoChip("重试", MaterialTheme.colorScheme.primary)
                if (failure.shouldUseVision) InfoChip("Vision", MaterialTheme.colorScheme.tertiary)
                if (failure.shouldUseLLMRepair) InfoChip("LLM修复", MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun RepairStatusCard(state: LoopState) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Build, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("修复状态", fontWeight = FontWeight.SemiBold)
                if (state.visionFallbackUsed) Text("✓ Vision 兜底已使用", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary)
                if (state.llmRepairUsed) Text("✓ LLM 修复已使用", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary)
                Text("修复尝试: ${state.repairAttempts}/${state.maxRepairAttempts}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TimelineLogItem(log: AgentLog) {
    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        .format(java.util.Date(log.timestamp))

    val statusIcon = when (log.status) {
        LogStatus.STARTED -> "▶"
        LogStatus.RUNNING -> "⟳"
        LogStatus.SUCCESS -> "✓"
        LogStatus.FAILED -> "✗"
        LogStatus.RETRYING -> "↻"
        LogStatus.SKIPPED -> "⊘"
    }

    val levelColor = when (log.level) {
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.WARN -> MaterialTheme.colorScheme.secondary
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.DEBUG -> MaterialTheme.colorScheme.outline
    }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(time, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.outline, fontSize = 10.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(statusIcon, fontSize = 10.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(log.message, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            color = levelColor, fontSize = 11.sp, maxLines = 2)
    }
}

@Composable
private fun InfoChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
