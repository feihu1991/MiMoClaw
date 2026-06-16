package com.xiaomi.mimoclaw.ui.screen

import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xiaomi.mimoclaw.data.remote.AuthManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit,
    onBack: () -> Unit,
    authManager: AuthManager = AuthManager()
) {
    var isLoading by remember { mutableStateOf(true) }
    var hasDetectedLogin by remember { mutableStateOf(false) }

    // 检查是否已有登录态
    LaunchedEffect(Unit) {
        val cookies = authManager.extractAuthCookies()
        val token = authManager.parseServiceToken(cookies)
        if (token != null) {
            onLoginSuccess(cookies ?: "")
            return@LaunchedEffect
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("小米账号登录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false

                            if (url != null && authManager.isCallbackUrl(url) && !hasDetectedLogin) {
                                hasDetectedLogin = true
                                view?.postDelayed({
                                    val cookies = authManager.extractAuthCookies()
                                    if (cookies != null) {
                                        onLoginSuccess(cookies)
                                    }
                                }, 1500)
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            return !(url.contains("xiaomi.com") || url.contains("mi.com") || url.contains("aistudio.xiaomimimo.com"))
                        }
                    }

                    loadUrl(authManager.buildLoginUrl())
                }
            }
        )
    }
}
