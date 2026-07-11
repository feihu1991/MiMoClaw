package com.xiaomi.mimoclaw.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaomi.mimoclaw.core.update.UpdateState
import com.xiaomi.mimoclaw.core.update.UpdateViewModel
import com.xiaomi.mimoclaw.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onHome: () -> Unit,
    onBrowser: () -> Unit,
    onFiles: () -> Unit,
    onBack: () -> Unit,
    updateViewModel: UpdateViewModel = hiltViewModel()
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    val updateState by updateViewModel.updateState.collectAsState()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("我的", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = false, onClick = onHome,
                    icon = { Icon(Icons.Default.Code, null) }, label = { Text("Claw") })
                NavigationBarItem(selected = false, onClick = onBrowser,
                    icon = { Icon(Icons.Outlined.Public, null) }, label = { Text("Chat") })
                NavigationBarItem(selected = false, onClick = onFiles,
                    icon = { Icon(Icons.Outlined.FolderOpen, null) }, label = { Text("文件") })
                NavigationBarItem(selected = true, onClick = { },
                    icon = { Icon(Icons.Filled.Person, null) }, label = { Text("我的") })
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
                    onClick = null
                )
                SettingItem(
                    icon = Icons.Outlined.TextFields,
                    title = "字体大小",
                    subtitle = "默认",
                    onClick = null
                )
                SettingItem(
                    icon = Icons.Outlined.Language,
                    title = "语言",
                    subtitle = "简体中文",
                    onClick = null
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
                Icon(Icons.AutoMirrored.Outlined.Logout, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("退出登录", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── 更新弹窗 (手动检查时) ──
    val updateInfo by updateViewModel.updateInfo.collectAsState()
    com.xiaomi.mimoclaw.core.update.UpdateDialog(
        updateState = updateState,
        updateInfo = updateInfo,
        onUpdate = { updateViewModel.startDownload() },
        onDismiss = { updateViewModel.dismissUpdate() },
        onRetry = { updateViewModel.checkUpdate() }
    )

    // ── 退出确认弹窗 ──
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
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
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
        if (onClick != null) {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        }
    }
}
