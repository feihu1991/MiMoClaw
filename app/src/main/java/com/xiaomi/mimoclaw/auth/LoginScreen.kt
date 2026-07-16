package com.xiaomi.mimoclaw.auth

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Login
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
        "login.xiaomi.com",
        "passport.xiaomi.com",
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
    val isLoading = loginState == LoginState.Loading
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
            LoginState.Success -> "登录成功，正在进入工作区"
            is LoginState.Error -> loginState.message
            LoginState.Idle -> statusMessage
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LoginBackdrop(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            MiMoBrandMark()
            Spacer(Modifier.height(24.dp))

            Text(
                text = "MiMo Claw",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "你的移动 AI 工作台",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))

            LoginTrustRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
            )
            Spacer(Modifier.height(28.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 3.dp,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "连接你的 MiMo 工作区",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "通过小米官方登录页完成身份验证，登录成功后即可进入全部功能。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))

                    LoginStatusBanner(
                        message = statusMessage,
                        isLoading = isLoading,
                        isError = loginState is LoginState.Error
                    )
                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = {
                            onResetState()
                            loginLauncher.launch(Intent(context, AuthWebViewActivity::class.java))
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Login,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isLoading) "正在验证…" else "使用小米账号登录",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "MiMo Claw 不会读取、保存或注入你的小米账号密码",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "登录后直接进入 MiMo Claw 工作区",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
