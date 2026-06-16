package com.xiaomi.mimoclaw.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomi.mimoclaw.data.model.ThemeMode
import com.xiaomi.mimoclaw.data.model.FontSize as AppFontSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var fontSize by remember { mutableStateOf(AppFontSize.MEDIUM) }
    var enableVoice by remember { mutableStateOf(false) }
    var enableNotification by remember { mutableStateOf(true) }
    var selectedModel by remember { mutableStateOf("MiMo-V2.5-Pro") }
    var showModelDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Subscription Section ──
            SettingsSectionHeader("Subscription") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Standard Annual",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = { /* Upgrade */ }) {
                            Text("Upgrade")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "End date: 2027-06-16 23:59:59 (UTC)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { /* Invoice */ }, modifier = Modifier.weight(1f)) {
                            Text("Invoice")
                        }
                        OutlinedButton(onClick = { /* Reset Plan Key */ }, modifier = Modifier.weight(1f)) {
                            Text("Reset Plan Key")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = { /* Details */ }) {
                        Text("Details")
                    }
                }
            }

            // ── Credits Usage ──
            SettingsSectionHeader("Credits Usage") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Used", style = MaterialTheme.typography.bodyMedium)
                        Text("1,243,846,540", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total", style = MaterialTheme.typography.bodyMedium)
                        Text("132,000,000,000")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { 0.01f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "1%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ── API Key ──
            SettingsSectionHeader("API Key") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Key, null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("API Key", fontWeight = FontWeight.Medium)
                        Text(
                            if (apiKeyVisible) "sk-xxxx...xxxx" else "••••••••••••",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null
                        )
                    }
                    IconButton(onClick = { /* Copy */ }) {
                        Icon(Icons.Default.ContentCopy, null)
                    }
                }
            }

            // ── Recovery ──
            SettingsSectionHeader("Recovery") {
                SettingsItem(
                    icon = Icons.Default.Build,
                    title = "Auto-Repair",
                    subtitle = "Automatically fix common issues",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.Refresh,
                    title = "Restart Gateway",
                    subtitle = "Restart the connection gateway",
                    onClick = { }
                )
                SettingsItem(
                    icon = Icons.Default.Restore,
                    title = "Reset to Default",
                    subtitle = "Reset all settings to default values",
                    onClick = { }
                )
            }

            // ── Model ──
            SettingsSectionHeader("Model") {
                SettingsItem(
                    icon = Icons.Default.SmartToy,
                    title = "Default Model",
                    subtitle = selectedModel,
                    onClick = { showModelDialog = true }
                )
            }

            // ── Appearance ──
            SettingsSectionHeader("Appearance") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { themeMode = mode },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Font Size", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppFontSize.entries.forEach { size ->
                            FilterChip(
                                selected = fontSize == size,
                                onClick = { fontSize = size },
                                label = { Text(size.label) }
                            )
                        }
                    }
                }
            }

            // ── Features ──
            SettingsSectionHeader("Features") {
                SettingsToggleItem(
                    icon = Icons.Default.Mic,
                    title = "Voice Input",
                    subtitle = "Enable voice-to-text input",
                    checked = enableVoice,
                    onCheckedChange = { enableVoice = it }
                )
                SettingsToggleItem(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "Receive push notifications",
                    checked = enableNotification,
                    onCheckedChange = { enableNotification = it }
                )
            }

            // ── Help ──
            SettingsSectionHeader("Help") {
                SettingsItem(icon = Icons.Default.Description, title = "Terms of Service", onClick = { })
                SettingsItem(icon = Icons.Default.PrivacyTip, title = "Privacy Policy", onClick = { })
                SettingsItem(icon = Icons.Default.Info, title = "Version", subtitle = "1.0.0", onClick = { })
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logout
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign out")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Model selection dialog
    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("Select Model") },
            text = {
                Column {
                    listOf("MiMo-V2.5-Pro", "MiMo-V2.5", "MiMo-V2", "MiMo-V1").forEach { model ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedModel == model, onClick = { selectedModel = model; showModelDialog = false })
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(model, fontWeight = FontWeight.Medium)
                                if (model == "MiMo-V2.5-Pro") {
                                    Text("Recommended", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModelDialog = false }) { Text("Cancel") } }
        )
    }

    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = { TextButton(onClick = { showLogoutDialog = false; onLogout() }) { Text("Sign out", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) { Column(content = content) }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsToggleItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
