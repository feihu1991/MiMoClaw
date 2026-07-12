package com.xiaomi.mimoclaw.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaomi.mimoclaw.feature.chat.model.ToolCall

/**
 * 可折叠的工具调用组卡片
 *
 * 参考 happy 项目的 ToolGroupView 设计：
 * - 更紧凑的布局
 * - 更明显的折叠状态
 * - 更简洁的样式
 */
@Composable
fun ToolGroupCard(
    tools: List<ToolCall>,
    isRunning: Boolean,
    isCollapsed: Boolean,
    onToggle: () -> Unit
) {
    if (tools.isEmpty()) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column {
            // 折叠头
            ToolGroupHeader(
                toolCount = tools.size,
                isRunning = isRunning,
                isCollapsed = isCollapsed,
                onToggle = onToggle
            )

            // 展开内容
            AnimatedVisibility(visible = !isCollapsed) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    tools.forEach { tool ->
                        ToolSummaryRow(tool)
                    }
                }
            }
        }
    }
}

/**
 * 工具组折叠头
 *
 * 参考 happy 的 CollapseHeader 设计：
 * - 更紧凑的布局
 * - 更明显的折叠状态
 */
@Composable
private fun ToolGroupHeader(
    toolCount: Int,
    isRunning: Boolean,
    isCollapsed: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 工具图标
        Icon(
            Icons.Default.Code,
            null,
            Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(Modifier.width(6.dp))

        // 摘要文本
        Text(
            "$toolCount 个工具调用",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        // 加载指示器
        if (isRunning) {
            Spacer(Modifier.width(6.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        Spacer(Modifier.weight(1f))

        // 展开/收起箭头
        Icon(
            if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            null,
            Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * 工具调用摘要行
 *
 * 参考 happy 的 ToolSummaryRow 设计：
 * - 更紧凑的布局
 * - 更简洁的样式
 */
@Composable
private fun ToolSummaryRow(tool: ToolCall) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 工具图标
        Icon(
            getToolIcon(tool.name),
            null,
            Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(Modifier.width(6.dp))

        // 工具名称
        Text(
            tool.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f)
        )

        // 状态 pill
        Surface(
            shape = RoundedCornerShape(3.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Text(
                tool.status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

/**
 * 工具图标映射
 */
private fun getToolIcon(toolName: String): ImageVector {
    return when {
        toolName.contains("bash", true) || toolName.contains("terminal", true) -> Icons.Default.Code
        toolName.contains("edit", true) || toolName.contains("write", true) -> Icons.Default.Description
        toolName.contains("search", true) || toolName.contains("grep", true) -> Icons.Default.Public
        else -> Icons.Default.AutoAwesome
    }
}
