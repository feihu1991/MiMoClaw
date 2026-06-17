package com.xiaomi.mimoclaw.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * 直接加载 aistudio.xiaomimimo.com，用户点击页面上的 "Sign in"
 * 会自动跳转到小米账号 SSO（带完整 sign/followup 参数）。
 * 登录成功后 SSO 回调到 /sts，页面跳回 aistudio 域名 → 检测登录成功。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    loginState: LoginState,
    onLoginSuccess: () -> Unit,
    onResetState: () -> Unit
) {
    val isLoading = remember { mutableStateOf(false) }
    val pageTitle = remember { mutableStateOf("") }

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
                modifier = Modifier.fillMaxSize()
            ) {
                // ── 顶部状态栏 ──
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Logo
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
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
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "MiMo Agent",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (pageTitle.value.isNotEmpty()) {
                                Text(
                                    pageTitle.value,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // 加载指示
                        if (isLoading.value) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                // ── WebView ──
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.mixedContentMode =
                                    WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                                    "(KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"

                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance()
                                    .setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(
                                        view: WebView?,
                                        pageUrl: String?,
                                        favicon: Bitmap?
                                    ) {
                                        isLoading.value = true
                                    }

                                    override fun onPageFinished(
                                        view: WebView?,
                                        pageUrl: String?
                                    ) {
                                        isLoading.value = false
                                        pageTitle.value = view?.title ?: ""

                                        // 登录成功检测:
                                        // SSO 回调到 /sts 后会跳回 aistudio 域名
                                        if (pageUrl != null &&
                                            pageUrl.contains("aistudio.xiaomimimo.com") &&
                                            !pageUrl.contains("account.xiaomi.com") &&
                                            !pageUrl.contains("/sts")
                                        ) {
                                            CookieManager.getInstance().flush()
                                            onLoginSuccess()
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean = false
                                }

                                // 直接加载 aistudio，页面上的 Sign in 按钮
                                // 会跳转到带完整参数的 SSO 页面
                                loadUrl("https://aistudio.xiaomimimo.com/")
                            }
                        }
                    )

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
        }
    }
}
