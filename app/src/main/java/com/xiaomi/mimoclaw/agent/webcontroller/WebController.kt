package com.xiaomi.mimoclaw.agent.webcontroller

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.webkit.*
import com.xiaomi.mimoclaw.data.model.ActionResult
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 增强版 WebView 控制器
 * 支持: open / click / input / extract / waitForElement / screenshot / scroll
 * 每个操作都带重试机制
 */
@Singleton
class WebController @Inject constructor() {

    private var webView: WebView? = null
    private val jsQueue = ConcurrentLinkedQueue<JsRequest>()
    private var isProcessing = false

    // ── 初始化 ──

    fun attachWebView(webView: WebView) {
        this.webView = webView
        webView.settings.apply {
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
        cookieManager.setAcceptThirdPartyCookies(webView, true)
    }

    fun detachWebView() {
        webView = null
    }

    // ── 核心操作 ──

    suspend fun open(url: String): ActionResult = withContext(Dispatchers.Main) {
        try {
            val wv = webView ?: return@withContext ActionResult(false, "WebView 未初始化")
            wv.loadUrl(url)
            delay(2000) // 等待页面加载
            waitForPageLoad(wv, 10000)
            ActionResult(true, "已打开: $url")
        } catch (e: Exception) {
            ActionResult(false, "打开失败: ${e.message}")
        }
    }

    suspend fun click(selector: String, retries: Int = 3): ActionResult = withContext(Dispatchers.Main) {
        repeat(retries) { attempt ->
            val js = """
                (function() {
                    var el = document.querySelector('$selector');
                    if (el) {
                        el.scrollIntoView({behavior: 'smooth', block: 'center'});
                        el.click();
                        return 'clicked';
                    }
                    return 'not_found';
                })();
            """.trimIndent()
            val result = evaluateJs(js)
            if (result == "clicked") {
                return@withContext ActionResult(true, "点击成功: $selector")
            }
            if (attempt < retries - 1) delay(1000)
        }
        ActionResult(false, "点击失败: 元素未找到 $selector")
    }

    suspend fun input(selector: String, text: String, retries: Int = 3): ActionResult = withContext(Dispatchers.Main) {
        repeat(retries) { attempt ->
            val escaped = text.replace("'", "\\'").replace("\n", "\\n")
            val js = """
                (function() {
                    var el = document.querySelector('$selector');
                    if (el) {
                        el.focus();
                        el.value = '$escaped';
                        el.dispatchEvent(new Event('input', {bubbles: true}));
                        el.dispatchEvent(new Event('change', {bubbles: true}));
                        return 'inputted';
                    }
                    return 'not_found';
                })();
            """.trimIndent()
            val result = evaluateJs(js)
            if (result == "inputted") {
                return@withContext ActionResult(true, "输入成功: $text")
            }
            if (attempt < retries - 1) delay(1000)
        }
        ActionResult(false, "输入失败: 元素未找到 $selector")
    }

    suspend fun extract(selector: String, retries: Int = 3): ActionResult = withContext(Dispatchers.Main) {
        repeat(retries) { attempt ->
            val js = """
                (function() {
                    var el = document.querySelector('$selector');
                    if (el) return el.innerText || el.textContent || '';
                    return '__NOT_FOUND__';
                })();
            """.trimIndent()
            val result = evaluateJs(js)
            if (result != "__NOT_FOUND__") {
                return@withContext ActionResult(true, "提取成功", data = result)
            }
            if (attempt < retries - 1) delay(1000)
        }
        ActionResult(false, "提取失败: 元素未找到 $selector")
    }

    suspend fun waitForElement(selector: String, timeoutMs: Long = 10000): ActionResult = withContext(Dispatchers.Main) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val js = """
                (function() {
                    var el = document.querySelector('$selector');
                    return el ? 'found' : 'not_found';
                })();
            """.trimIndent()
            if (evaluateJs(js) == "found") {
                return@withContext ActionResult(true, "元素已出现: $selector")
            }
            delay(500)
        }
        ActionResult(false, "等待超时: $selector")
    }

    suspend fun scroll(direction: String, pixels: Int = 500): ActionResult = withContext(Dispatchers.Main) {
        val js = when (direction) {
            "down" -> "window.scrollBy(0, $pixels); 'scrolled'"
            "up" -> "window.scrollBy(0, -$pixels); 'scrolled'"
            "left" -> "window.scrollBy(-$pixels, 0); 'scrolled'"
            "right" -> "window.scrollBy($pixels, 0); 'scrolled'"
            else -> "window.scrollBy(0, $pixels); 'scrolled'"
        }
        evaluateJs(js)
        ActionResult(true, "滚动: $direction ${pixels}px")
    }

    suspend fun navigateBack(): ActionResult = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext ActionResult(false, "WebView 未初始化")
        wv.evaluateJavascript("history.back()", null)
        delay(1000)
        ActionResult(true, "已返回")
    }

    suspend fun screenshot(): ActionResult = withContext(Dispatchers.Main) {
        try {
            val wv = webView ?: return@withContext ActionResult(false, "WebView 未初始化")
            val bitmap = captureWebView(wv) ?: return@withContext ActionResult(false, "截图失败")
            val base64 = bitmapToBase64(bitmap)
            ActionResult(true, "截图成功", data = base64)
        } catch (e: Exception) {
            ActionResult(false, "截图失败: ${e.message}")
        }
    }

    suspend fun getPageSource(): ActionResult = withContext(Dispatchers.Main) {
        val js = "document.documentElement.outerHTML"
        val result = evaluateJs(js)
        ActionResult(true, "页面源码", data = result)
    }

    suspend fun getCurrentUrl(): String = withContext(Dispatchers.Main) {
        webView?.url ?: ""
    }

    suspend fun getPageTitle(): String = withContext(Dispatchers.Main) {
        webView?.title ?: ""
    }

    // ── DOM 不可用时的 Vision 兜底 ──

    suspend fun clickAtCoordinate(x: Float, y: Float): ActionResult = withContext(Dispatchers.Main) {
        try {
            val wv = webView ?: return@withContext ActionResult(false, "WebView 未初始化")
            val dispatchJs = """
                (function() {
                    var ev = new MouseEvent('click', {
                        clientX: $x, clientY: $y,
                        bubbles: true, cancelable: true
                    });
                    document.elementFromPoint($x, $y)?.dispatchEvent(ev);
                    return 'clicked';
                })();
            """.trimIndent()
            evaluateJs(dispatchJs)
            ActionResult(true, "坐标点击: ($x, $y)")
        } catch (e: Exception) {
            ActionResult(false, "坐标点击失败: ${e.message}")
        }
    }

    suspend fun inputAtCoordinate(x: Float, y: Float, text: String): ActionResult = withContext(Dispatchers.Main) {
        try {
            val escaped = text.replace("'", "\\'")
            val js = """
                (function() {
                    var el = document.elementFromPoint($x, $y);
                    if (el) {
                        el.focus();
                        el.value = '$escaped';
                        el.dispatchEvent(new Event('input', {bubbles: true}));
                        return 'inputted';
                    }
                    return 'not_found';
                })();
            """.trimIndent()
            val result = evaluateJs(js)
            if (result == "inputted") ActionResult(true, "坐标输入: $text")
            else ActionResult(false, "坐标输入失败")
        } catch (e: Exception) {
            ActionResult(false, "坐标输入失败: ${e.message}")
        }
    }

    // ── Cookie 管理 ──

    fun getCookies(url: String): String? {
        return CookieManager.getInstance().getCookie(url)
    }

    fun setCookies(url: String, cookies: String) {
        CookieManager.getInstance().setCookie(url, cookies)
    }

    // ── 内部方法 ──

    private suspend fun evaluateJs(js: String): String = suspendCancellableCoroutine { cont ->
        val wv = webView
        if (wv == null) {
            if (cont.isActive) cont.resume("") {}
            return@suspendCancellableCoroutine
        }
        wv.evaluateJavascript(js) { result ->
            val cleaned = result?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: ""
            if (cont.isActive) cont.resume(cleaned) {}
        }
    }

    private suspend fun waitForPageLoad(webView: WebView, timeoutMs: Long) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val readyState = evaluateJs("document.readyState")
            if (readyState == "complete") return
            delay(300)
        }
    }

    private fun captureWebView(webView: WebView): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            webView.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private data class JsRequest(
        val js: String,
        val callback: (String) -> Unit
    )
}
