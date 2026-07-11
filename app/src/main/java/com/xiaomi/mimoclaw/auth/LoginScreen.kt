package com.xiaomi.mimoclaw.auth

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.net.URI

/** Exact URL policy for the Xiaomi SSO top-level navigation. */
object SsoNavigationPolicy {
    private val allowedHosts = setOf(
        "account.xiaomi.com",
        "aistudio.xiaomimimo.com",
        "mi.com",
        "www.mi.com"
    )

    fun isAllowed(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.lowercase() in allowedHosts
    }
}

object CookieHeader {
    fun contains(cookieHeader: String?, name: String): Boolean {
        if (cookieHeader.isNullOrBlank() || name.isBlank()) return false
        return cookieHeader.split(';').any { part ->
            part.trim().substringBefore('=', missingDelimiterValue = "") == name
        }
    }
}

@Composable
fun LoginScreen(
    loginState: LoginState,
    onResetState: () -> Unit = {},
    onSsoSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    var statusMessage by remember { mutableStateOf("登录过程仅在小米官方页面中完成") }
    val loginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            statusMessage = "正在验证登录状态…"
            onSsoSuccess()
        } else if (result.resultCode == AuthWebViewActivity.RESULT_LOGIN_ERROR) {
            statusMessage = result.data?.getStringExtra(AuthWebViewActivity.EXTRA_ERROR)
                ?: "登录页面无法打开，请检查网络后重试"
        }
    }

    LaunchedEffect(loginState) {
        statusMessage = when (loginState) {
            LoginState.Loading -> "正在验证登录状态…"
            LoginState.Success -> "登录成功"
            is LoginState.Error -> loginState.message
            LoginState.Idle -> statusMessage
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "登录 MiMo Claw",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                statusMessage,
                color = if (loginState is LoginState.Error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    onResetState()
                    loginLauncher.launch(Intent(context, AuthWebViewActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("使用小米账号登录", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "应用不会读取、保存或注入你的小米账号密码",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
