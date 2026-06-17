package com.xiaomi.mimoclaw.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.xiaomi.mimoclaw.platform.billing.*
import com.xiaomi.mimoclaw.platform.tenant.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(
    user: TenantUser?,
    quota: UserQuota?,
    dailyUsage: DailyUsage,
    usageSummary: UsageSummary,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用量 & 计费") },
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
            // 当前套餐
            item {
                PlanCard(user?.plan ?: PlanType.FREE)
            }
            
            // 配额
            if (quota != null) {
                item {
                    QuotaCard(quota)
                }
            }
            
            // 今日用量
            item {
                DailyUsageCard(dailyUsage)
            }
            
            // 累计用量
            item {
                TotalUsageCard(usageSummary)
            }
            
            // 计费明细
            item {
                PricingCard()
            }
            
            // 升级入口
            item {
                UpgradeCard()
            }
        }
    }
}

@Composable
private fun PlanCard(plan: PlanType) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (plan == PlanType.FREE) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("当前套餐", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text(plan.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(plan.price, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (plan != PlanType.ENTERPRISE) {
                FilledTonalButton(onClick = { }) {
                    Text("升级")
                }
            }
        }
    }
}

@Composable
private fun QuotaCard(quota: UserQuota) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("本月配额", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            
            QuotaItem("任务", quota.tasksRemaining, if (quota.tasksRemaining < 0) 999 else quota.tasksRemaining + 10)
            Spacer(modifier = Modifier.height(8.dp))
            QuotaItem("Token", quota.tokensRemaining, if (quota.tokensRemaining < 0) 999999 else quota.tokensRemaining + 100000)
            Spacer(modifier = Modifier.height(8.dp))
            QuotaItem("Vision调用", quota.visionCallsRemaining, if (quota.visionCallsRemaining < 0) 999 else quota.visionCallsRemaining + 10)
        }
    }
}

@Composable
private fun QuotaItem(label: String, remaining: Long, total: Long) {
    val progress = if (total > 0) (total - remaining).toFloat() / total else 0f
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(60.dp), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(8.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            if (remaining < 0) "无限" else remaining.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DailyUsageCard(usage: DailyUsage) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("今日用量", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                UsageItem("Token", usage.tokens.toString())
                UsageItem("Vision", usage.visionCalls.toString())
                UsageItem("任务", usage.tasks.toString())
                UsageItem("费用", "¥${String.format("%.2f", usage.cost)}")
            }
        }
    }
}

@Composable
private fun TotalUsageCard(summary: UsageSummary) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("累计用量", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                UsageItem("Token", formatNumber(summary.totalTokens))
                UsageItem("Vision", summary.totalVisionCalls.toString())
                UsageItem("任务", summary.totalTasks.toString())
                UsageItem("总费用", "¥${String.format("%.2f", summary.totalCost)}")
            }
        }
    }
}

@Composable
private fun UsageItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun PricingCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("计费标准", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            PricingItem("MiMo-V2.5-Pro Token", "¥0.02/1K tokens")
            PricingItem("MiMo-V2.5 Token", "¥0.01/1K tokens")
            PricingItem("Vision 调用", "¥0.05/次")
            PricingItem("任务执行", "免费")
        }
    }
}

@Composable
private fun PricingItem(name: String, price: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Text(price, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun UpgradeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("需要更多配额？", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("升级到 Pro 或 Team 获取更多任务、Token和Vision配额",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                Text("查看套餐")
            }
        }
    }
}

private fun formatNumber(n: Long): String {
    return when {
        n >= 1_000_000 -> "${n / 1_000_000}M"
        n >= 1_000 -> "${n / 1_000}K"
        else -> n.toString()
    }
}
