package com.xiaomi.mimoclaw.ui.screen

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.xiaomi.mimoclaw.agent.webcontroller.WebController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    webController: WebController,
    currentUrl: String,
    onBack: () -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("浏览器", style = MaterialTheme.typography.titleSmall)
                        if (pageTitle.isNotEmpty()) {
                            Text(pageTitle, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            )
        },
        bottomBar = {
            // URL 栏
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入网址") },
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (url.isNotBlank()) {
                                if (!url.startsWith("http")) url = "https://$url"
                                webController.let { /* load url via WebView */ }
                            }
                        }
                    ) {
                        Icon(Icons.Default.OpenInBrowser, "打开")
                    }
                }
            }
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding),
            factory = { context ->
                WebView(context).apply {
                    webController.attachWebView(this)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                            isLoading = true
                            pageUrl?.let { url = it }
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            pageTitle = view?.title ?: ""
                        }
                    }
                    webChromeClient = object : WebChromeClient() {}

                    if (currentUrl.isNotEmpty()) {
                        loadUrl(currentUrl)
                    }
                }
            }
        )
    }
}
