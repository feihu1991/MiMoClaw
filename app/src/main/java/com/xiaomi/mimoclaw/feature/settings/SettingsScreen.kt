package com.xiaomi.mimoclaw.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = false, onClick = onBack,
                    icon = { Icon(Icons.Default.Home, null) }, label = { Text("首页") })
                NavigationBarItem(selected = false, onClick = { },
                    icon = { Icon(Icons.Outlined.Assignment, null) }, label = { Text("任务") })
                NavigationBarItem(selected = false, onClick = { },
                    icon = { Icon(Icons.Outlined.Public, null) }, label = { Text("浏览") })
                NavigationBarItem(selected = true, onClick = { },
                    icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("设置") })
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── 外观 ──
            SectionHeader("外观") {
                SettingItem(
                    icon = Icons.Outlined.Palette,
                    title = "主题",
                    subtitle = "跟随系统",
                    onClick = { }
                )
                SettingItem(
                    icon = Icons.Outlined.TextFields,
                    title = "字体大小",
                    subtitle = "默认",
                    onClick = { }
                )
            }

            // ── Agent ──
            SectionHeader("Agent") {
                SettingItem(
                    icon = Icons.Outlined.SmartToy,
                    title = "默认模型",
                    subtitle = "MiMo-V2.5-Pro",
                    onClick = { }
                )
                SettingItem(
                    icon = Icons.Outlined.Tune,
                    title = "最大重试次数",
                    subtitle = "3 次",
                    onClick = { }
                )
            }

            // ── 数据 ──
            SectionHeader("数据") {
                SettingItem(
                    icon = Icons.Outlined.Storage,
                    title = "缓存管理",
                    subtitle = "清除缓存数据",
                    onClick = { }
                )
                SettingItem(
                    icon = Icons.Outlined.History,
                    title = "任务历史",
                    subtitle = "管理历史记录",
                    onClick = { }
                )
            }

            // ── 关于 ──
            SectionHeader("关于") {
                SettingItem(
                    icon = Icons.Outlined.Info,
                    title = "版本",
                    subtitle = "3.0.0",
                    onClick = { }
                )
                SettingItem(
                    icon = Icons.Outlined.Description,
                    title = "服务协议",
                    onClick = { }
                )
                SettingItem(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "隐私政策",
                    onClick = { }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 退出登录 ──
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Outlined.Logout, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("退出登录", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── 退出确认弹窗 ──
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.Outlined.Logout, null) },
            title = { Text("退出登录") },
            text = { Text("确定要退出登录吗？退出后需要重新登录才能使用。") },
            confirmButton = {
                TextButton(
                    onClick = { showLogoutDialog = false; onLogout() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Column(content = content) }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
    }
}
