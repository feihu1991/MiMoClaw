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
import com.xiaomi.mimoclaw.platform.event.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiAgentScreen(
    activeTasks: Map<String, Any>, // OrchestratorTaskState
    eventHistory: List<AgentMessage>,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Multi-Agent 执行") },
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
            // Agent 状态卡片
            item {
                Text("Agent 状态", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AgentStatusCard("Planner", AgentType.PLANNER, Modifier.weight(1f))
                    AgentStatusCard("Worker", AgentType.WORKER, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AgentStatusCard("Critic", AgentType.CRITIC, Modifier.weight(1f))
                    AgentStatusCard("Vision", AgentType.VISION, Modifier.weight(1f))
                }
            }
            
            // 事件流
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("事件流", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            
            if (eventHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无事件", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                items(eventHistory.takeLast(30).reversed()) { event ->
                    EventCard(event)
                }
            }
        }
    }
}

@Composable
private fun AgentStatusCard(name: String, type: AgentType, modifier: Modifier = Modifier) {
    val color = when (type) {
        AgentType.PLANNER -> MaterialTheme.colorScheme.primary
        AgentType.WORKER -> MaterialTheme.colorScheme.tertiary
        AgentType.CRITIC -> MaterialTheme.colorScheme.error
        AgentType.VISION -> MaterialTheme.colorScheme.secondary
        AgentType.ORCHESTRATOR -> MaterialTheme.colorScheme.outline
    }
    
    val icon = when (type) {
        AgentType.PLANNER -> Icons.Default.Psychology
        AgentType.WORKER -> Icons.Default.Build
        AgentType.CRITIC -> Icons.Default.BugReport
        AgentType.VISION -> Icons.Default.Visibility
        AgentType.ORCHESTRATOR -> Icons.Default.Hub
    }
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(name, fontWeight = FontWeight.SemiBold, color = color)
            Text("在线", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun EventCard(event: AgentMessage) {
    val typeColor = when (event.type) {
        MessageType.PLAN_CREATED -> MaterialTheme.colorScheme.primary
        MessageType.STEP_ASSIGNED -> MaterialTheme.colorScheme.secondary
        MessageType.STEP_COMPLETED -> MaterialTheme.colorScheme.tertiary
        MessageType.STEP_FAILED -> MaterialTheme.colorScheme.error
        MessageType.REPAIR_SUGGESTED -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        MessageType.VISION_REQUESTED -> MaterialTheme.colorScheme.secondary
        MessageType.VISION_RESULT -> MaterialTheme.colorScheme.tertiary
        MessageType.RETRY_APPROVED -> MaterialTheme.colorScheme.primary
        MessageType.SKIP_APPROVED -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline
    }
    
    val typeIcon = when (event.type) {
        MessageType.PLAN_CREATED -> "📋"
        MessageType.STEP_ASSIGNED -> "📤"
        MessageType.STEP_COMPLETED -> "✅"
        MessageType.STEP_FAILED -> "❌"
        MessageType.REPAIR_SUGGESTED -> "🔧"
        MessageType.VISION_REQUESTED -> "👁️"
        MessageType.VISION_RESULT -> "🎯"
        MessageType.RETRY_APPROVED -> "🔄"
        MessageType.SKIP_APPROVED -> "⏭️"
        MessageType.TASK_PAUSED -> "⏸️"
        MessageType.TASK_RESUMED -> "▶️"
        MessageType.TASK_CANCELLED -> "🚫"
        MessageType.ERROR -> "⚠️"
        MessageType.LOG -> "📝"
        MessageType.HEARTBEAT -> "💓"
    }
    
    val agentLabel = when (event.agentType) {
        AgentType.PLANNER -> "Planner"
        AgentType.WORKER -> "Worker"
        AgentType.CRITIC -> "Critic"
        AgentType.VISION -> "Vision"
        AgentType.ORCHESTRATOR -> "Orch"
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(typeIcon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = typeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            agentLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = typeColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        event.type.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = typeColor
                    )
                }
                
                // 显示payload信息
                if (event.payload.instruction != null) {
                    Text(event.payload.instruction, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                if (event.payload.error != null) {
                    Text(event.payload.error, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error, maxLines = 2)
                }
                if (event.result?.data != null) {
                    Text("结果: ${event.result.data}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary, maxLines = 1)
                }
            }
            
            Text(
                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(event.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
