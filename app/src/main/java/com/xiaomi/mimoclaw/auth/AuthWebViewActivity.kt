package com.xiaomi.mimoclaw.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Isolated native Activity for Xiaomi SSO. Keeping the WebView outside Compose avoids
 * AndroidView composition/rendering issues while preserving the app-owned CookieManager.
 */
class AuthWebViewActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView

    private val cookieCheck = object : Runnable {
        override fun run() {
            val cookies = CookieManager.getInstance().getCookie(STUDIO_ORIGIN)
            if (
                CookieHeader.contains(cookies, "serviceToken") &&
                CookieHeader.contains(cookies, "xiaomichatbot_ph")
            ) {
                CookieManager.getInstance().flush()
                setResult(Activity.RESULT_OK)
                finish()
                return
            }
            handler.postDelayed(this, COOKIE_CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "小米账号登录"
        setContentView(buildContent())
        configureWebView()
        handler.post(cookieCheck)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            useWideViewPort = false
            loadWithOverviewMode = false
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (!SsoNavigationPolicy.isAllowed(url)) {
                    showError("已阻止跳转到非小米官方页面")
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress.visibility = ProgressBar.VISIBLE
                status.text = "正在打开小米官方登录页…"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = ProgressBar.GONE
                status.text = "请在小米官方页面完成登录"
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    showError("登录页加载失败，请检查网络后重试")
                }
            }
        }
        webView.loadUrl(LOGIN_URL)
    }

    private fun buildContent(): ViewGroup {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        val close = TextView(this).apply {
            text = "关闭"
            textSize = 16f
            setTextColor(Color.rgb(35, 89, 190))
            setOnClickListener { finish() }
        }
        status = TextView(this).apply {
            text = "正在打开小米官方登录页…"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
        }
        header.addView(close, LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))
        header.addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val content = FrameLayout(this)
        webView = WebView(this)
        progress = ProgressBar(this).apply { isIndeterminate = true }
        content.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(progress, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun showError(message: String) {
        progress.visibility = ProgressBar.GONE
        status.text = message
        setResult(RESULT_LOGIN_ERROR, intent.putExtra(EXTRA_ERROR, message))
    }

    override fun onDestroy() {
        handler.removeCallbacks(cookieCheck)
        webView.apply {
            stopLoading()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val STUDIO_ORIGIN = "https://aistudio.xiaomimimo.com"
        const val LOGIN_URL = "$STUDIO_ORIGIN/open-apis/v1/genLoginUrl"
        const val COOKIE_CHECK_INTERVAL_MS = 1_500L
        const val RESULT_LOGIN_ERROR = Activity.RESULT_FIRST_USER
        const val EXTRA_ERROR = "login_error"
    }
}
