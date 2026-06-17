package com.xiaomi.mimoclaw.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 登录界面
 *
 * 使用小米账号 SSO 登录，与 Web 端 (aistudio.xiaomimimo.com) 保持一致。
 * 登录流程：
 * 1. 打开小米账号 SSO 页面
 * 2. 用户在 WebView 中完成登录
 * 3. 登录成功后回调到 STS 接口
 * 4. 检测到登录成功后获取用户信息
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    loginState: LoginState,
    onLoginSuccess: () -> Unit,
    onResetState: () -> Unit
) {
    var isLoading by remember { mutableStateState(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // 监听登录成功
    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            onLoginSuccess()
        }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(60.dp))

                // ── Logo ──
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Mi",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── 欢迎文字 ──
                Text(
                    "欢迎使用 MiMo Agent",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "使用小米账号登录以继续",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── SSO WebView ──
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        setSupportZoom(true)
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    }

                                    // 同步 Cookie 到 OkHttp
                                    CookieManager.getInstance().apply {
                                        setAcceptCookie(true)
                                        setAcceptThirdPartyCookies(this@apply, true)
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(
                                            view: WebView?,
                                            pageUrl: String?,
                                            favicon: Bitmap?
                                        ) {
                                            isLoading = true
                                        }

                                        override fun onPageFinished(
                                            view: WebView?,
                                            pageUrl: String?
                                        ) {
                                            isLoading = false

                                            // 检测是否登录成功
                                            // SSO 登录成功后会跳转到 aistudio.xiaomimimo.com
                                            if (pageUrl != null &&
                                                pageUrl.contains("aistudio.xiaomimimo.com") &&
                                                !pageUrl.contains("account.xiaomi.com")
                                            ) {
                                                // 同步 Cookie 到 OkHttp
                                                CookieManager.getInstance().flush()
                                                onLoginSuccess()
                                            }
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            return false
                                        }
                                    }

                                    // 加载小米账号 SSO 页面
                                    loadUrl(AuthRepository.SSO_LOGIN_URL)
                                    webView = this
                                }
                            }
                        )

                        // 加载指示器
                        if (isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                            )
                        }

                        // 错误提示
                        if (loginState is LoginState.Error) {
                            Snackbar(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp),
                                action = {
                                    TextButton(onClick = onResetState) {
                                        Text("重试")
                                    }
                                }
                            ) {
                                Text(loginState.message)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── 底部提示 ──
                Text(
                    "登录即表示您同意小米账号用户协议和隐私政策",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
}
