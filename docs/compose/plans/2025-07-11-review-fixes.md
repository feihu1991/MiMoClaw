# 代码审查全部修复 Implementation Plan

> [!NOTE]
> This document may not reflect the current implementation.
> See the final report for up-to-date state:
> [Final Report](../reports/review-fixes.md)

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复代码审查发现的全部 9 项问题 — 2 Critical + 7 Important

**Architecture:** 按文件分组修复，每个 Task 聚焦一个文件或一组紧密相关的修改。最小化代码变更，不引入新功能。

**Tech Stack:** Kotlin, Jetpack Compose, OkHttp, Hilt, DataStore

## Global Constraints

- 不改变任何功能行为，仅修复安全/质量问题
- 不引入新依赖
- 保持现有 API 接口不变
- Log.e 仅用于错误路径，正常流程改用 Log.d

---

### Task 1: LoginScreen — 清除硬编码凭据 + 重命名函数

**Covers:** Issue #1 (Critical), #9 (Important)

**Files:**
- Modify: `app/src/main/java/com/xiaomi/mimoclaw/auth/LoginScreen.kt`

**Interfaces:**
- 无下游依赖

- [ ] **Step 1: 删除所有硬编码 fallback 值**

LoginScreen.kt:289 — 删除 `account.ifBlank { "2350085810" }` fallback：
```kotlin
// Before:
Text(
    account.ifBlank { "2350085810" },
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

// After:
Text(
    account,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)
```

LoginScreen.kt:328-329 — 删除 account/password fallback：
```kotlin
// Before:
val acc = account.ifBlank { "2350085810" }
val pwd = password.ifBlank { "641100du" }

// After:
val acc = account
val pwd = password
```

LoginScreen.kt:330-334 — 添加空值保护，凭据为空时不注入：
```kotlin
// Before:
view?.postDelayed({
    view.evaluateJavascript(
        fillAndSubmitJs(acc, pwd), null
    )
}, 1500)

// After:
if (acc.isNotBlank() && pwd.isNotBlank()) {
    view?.postDelayed({
        view.evaluateJavascript(
            fillCredentialsJs(acc, pwd), null
        )
    }, 1500)
}
```

- [ ] **Step 2: 重命名函数 fillAndSubmitJs → fillCredentialsJs**

LoginScreen.kt:379 — 函数定义：
```kotlin
// Before:
private fun fillAndSubmitJs(acc: String, pwd: String): String {

// After:
private fun fillCredentialsJs(acc: String, pwd: String): String {
```

更新函数注释：
```kotlin
/**
 * 生成 JS 代码: 填充凭据到小米官方登录表单 (仅填充, 不自动提交)
 */
private fun fillCredentialsJs(acc: String, pwd: String): String {
```

- [ ] **Step 3: 验证编译通过**

Run: `cd C:\workspace\MiMoClaw && .\gradlew.bat compileDebugKotlin 2>&1 | Select-String -Pattern "error:|BUILD"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/xiaomi/mimoclaw/auth/LoginScreen.kt
git commit -m "fix: 清除硬编码凭据, 重命名 fillCredentialsJs"
```

---

### Task 2: LoginScreen — 无限轮询添加超时保护

**Covers:** Issue #3 (Important)

**Files:**
- Modify: `app/src/main/java/com/xiaomi/mimoclaw/auth/LoginScreen.kt`

**Interfaces:**
- 无下游依赖

- [ ] **Step 1: 给 Cookie 轮询添加超时**

LoginScreen.kt:107-123 — 替换无限循环：
```kotlin
// Before:
LaunchedEffect(step) {
    if (step < 1) return@LaunchedEffect
    while (true) {
        kotlinx.coroutines.delay(1500)
        val cookies = CookieManager.getInstance().getCookie("https://aistudio.xiaomimimo.com")
        if (cookies != null &&
            cookies.contains("serviceToken") &&
            cookies.contains("xiaomichatbot_ph")
        ) {
            CookieManager.getInstance().flush()
            statusText = "登录成功"
            kotlinx.coroutines.delay(400)
            onSsoSuccess()
            return@LaunchedEffect
        }
    }
}

// After:
LaunchedEffect(step) {
    if (step < 1) return@LaunchedEffect
    val maxPolls = 120 // 120 * 1.5s = 3 分钟超时
    var polls = 0
    while (polls < maxPolls) {
        kotlinx.coroutines.delay(1500)
        polls++
        val cookies = CookieManager.getInstance().getCookie("https://aistudio.xiaomimimo.com")
        if (cookies != null &&
            cookies.contains("serviceToken") &&
            cookies.contains("xiaomichatbot_ph")
        ) {
            CookieManager.getInstance().flush()
            statusText = "登录成功"
            kotlinx.coroutines.delay(400)
            onSsoSuccess()
            return@LaunchedEffect
        }
    }
    statusError = "登录超时, 请重试"
    submitting = false
    step = 0
}
```

- [ ] **Step 2: 验证编译通过**

Run: `cd C:\workspace\MiMoClaw && .\gradlew.bat compileDebugKotlin 2>&1 | Select-String -Pattern "error:|BUILD"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/xiaomi/mimoclaw/auth/LoginScreen.kt
git commit -m "fix: LoginScreen Cookie 轮询添加 3 分钟超时保护"
```

---

### Task 3: MainActivity — 修复 BroadcastReceiver 泄漏 + 消除 GlobalScope

**Covers:** Issue #4 (Important)

**Files:**
- Modify: `app/src/main/java/com/xiaomi/mimoclaw/MainActivity.kt`

**Interfaces:**
- 无下游依赖

- [ ] **Step 1: 用成员变量持有 receiver，onDestroy 中 unregister**

MainActivity.kt — 重构：
```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var gateway: com.xiaomi.mimoclaw.core.network.ClawGateway

    private var cookieReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 注册广播接收器 - 用于从 adb 注入 Cookie
        cookieReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != "MIMO_SET_COOKIE") return
                val cookies = intent.getStringExtra("cookies") ?: return
                val cm = CookieManager.getInstance()
                cm.removeAllCookies(null)
                for (part in cookies.split(";")) {
                    val trimmed = part.trim()
                    if (trimmed.contains("=")) {
                        cm.setCookie("https://aistudio.xiaomimimo.com", trimmed)
                        cm.setCookie("https://account.xiaomi.com", trimmed)
                        android.util.Log.d("MainActivity", "注入 cookie: $trimmed")
                    }
                }
                cm.flush()
                android.util.Log.d("MainActivity", "Cookie 注入完成, 重新连接...")
                gateway.disconnect()
                lifecycleScope.launch(Dispatchers.IO) {
                    delay(500)
                    gateway.connect()
                }
            }
        }
        registerReceiver(cookieReceiver, IntentFilter("MIMO_SET_COOKIE"), RECEIVER_NOT_EXPORTED)

        setContent {
            // ... existing code unchanged ...
        }
    }

    override fun onDestroy() {
        cookieReceiver?.let {
            unregisterReceiver(it)
            cookieReceiver = null
        }
        super.onDestroy()
    }
}
```

- [ ] **Step 2: 验证编译通过**

Run: `cd C:\workspace\MiMoClaw && .\gradlew.bat compileDebugKotlin 2>&1 | Select-String -Pattern "error:|BUILD"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/xiaomi/mimoclaw/MainActivity.kt
git commit -m "fix: 修复 BroadcastReceiver 泄漏, 用 lifecycleScope 替代 GlobalScope"
```

---

### Task 4: AuthViewModel — CookieManager 线程安全

**Covers:** Issue #5 (Important)

**Files:**
- Modify: `app/src/main/java/com/xiaomi/mimoclaw/auth/AuthViewModel.kt`

**Interfaces:**
- 无下游依赖

- [ ] **Step 1: 确保 CookieManager 操作在主线程**

AuthViewModel.kt:31-51 — init 块中 Cookie 读取需在主线程：
```kotlin
// Before:
init {
    viewModelScope.launch {
        delay(500)

        // 已有有效 Cookie (上次登录态), 直接进入
        val cookies = CookieManager.getInstance().getCookie("https://aistudio.xiaomimimo.com")
        ...

// After:
init {
    viewModelScope.launch {
        delay(500)

        // CookieManager 操作必须在主线程
        val cookies = withContext(kotlinx.coroutines.Dispatchers.Main) {
            CookieManager.getInstance().getCookie("https://aistudio.xiaomimimo.com")
        }
        ...
```

AuthViewModel.kt:66-68 — onSsoLoginSuccess 中同样修复：
```kotlin
// Before:
onFailure = {
    val cookies = CookieManager.getInstance().getCookie("https://aistudio.xiaomimimo.com")
    ...

// After:
onFailure = {
    val cookies = withContext(kotlinx.coroutines.Dispatchers.Main) {
        CookieManager.getInstance().getCookie("https://aistudio.xiaomimimo.com")
    }
    ...
```

需要在文件顶部添加 import：
```kotlin
import kotlinx.coroutines.withContext
```

- [ ] **Step 2: 验证编译通过**

Run: `cd C:\workspace\MiMoClaw && .\gradlew.bat compileDebugKotlin 2>&1 | Select-String -Pattern "error:|BUILD"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/xiaomi/mimoclaw/auth/AuthViewModel.kt
git commit -m "fix: AuthViewModel CookieManager 操作切换到主线程"
```

---

### Task 5: ClawGateway — CoroutineScope 异常处理 + OkHttpClient 复用

**Covers:** Issue #6, #7 (Important)

**Files:**
- Modify: `app/src/main/java/com/xiaomi/mimoclaw/core/network/ClawGateway.kt`

**Interfaces:**
- 无下游依赖

- [ ] **Step 1: 添加 CoroutineExceptionHandler**

ClawGateway.kt:55 — 替换 scope 定义：
```kotlin
// Before:
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

// After:
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() +
    CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine 未捕获异常", throwable)
    })
```

- [ ] **Step 2: 复用 WebSocket OkHttpClient 实例**

ClawGateway.kt:164-174 — 提取为成员变量：
```kotlin
// Before (在 connectWebSocket 方法内部):
val wsClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.MILLISECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .addInterceptor { chain ->
        val req = chain.request().newBuilder()
            .header("Cookie", CookieManager.getInstance().getCookie(REST_BASE) ?: "")
            .build()
        chain.proceed(req)
    }
    .build()

// After (提取为 companion 或成员变量):
companion object {
    private const val TAG = "ClawGateway"
    private const val WS_BASE = "wss://aistudio.xiaomimimo.com/ws/proxy"
    private const val REST_BASE = "https://aistudio.xiaomimimo.com"

    private val wsClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Cookie", CookieManager.getInstance().getCookie(REST_BASE) ?: "")
                    .build()
                chain.proceed(req)
            }
            .build()
    }
}
```

然后 `connectWebSocket` 中直接使用 `wsClient`：
```kotlin
webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
```

- [ ] **Step 3: 验证编译通过**

Run: `cd C:\workspace\MiMoClaw && .\gradlew.bat compileDebugKotlin 2>&1 | Select-String -Pattern "error:|BUILD"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/xiaomi/mimoclaw/core/network/ClawGateway.kt
git commit -m "fix: ClawGateway 添加 CoroutineExceptionHandler, 复用 OkHttpClient"
```

---

### Task 6: Conversation — MutableList 改为不可变 List

**Covers:** Issue #8 (Important)

**Files:**
- Modify: `app/src/main/java/com/xiaomi/mimoclaw/feature/chat/model/ChatMessage.kt`
- Modify: `app/src/main/java/com/xiaomi/mimoclaw/feature/chat/ChatViewModel.kt`

**Interfaces:**
- `Conversation.messages` 类型从 `MutableList<ChatMessage>` 改为 `List<ChatMessage>`
- 所有消费 `Conversation.messages` 的代码需要适配

- [ ] **Step 1: 修改 Conversation 数据模型**

ChatMessage.kt:28-36：
```kotlin
// Before:
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    val model: String = "mimo-v2.5-pro",
    val sessionKey: String? = null
)

// After:
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val model: String = "mimo-v2.5-pro",
    val sessionKey: String? = null
)
```

- [ ] **Step 2: 适配 ChatViewModel 中的所有消息操作**

ChatViewModel.kt:96-98 — sendMessage 中 toMutableList() 已在使用，无需改：
```kotlin
val msgs1 = conv.messages.toMutableList()
msgs1.add(ChatMessage(role = ChatMessage.Role.USER, content = text))
_currentConversation.value = conv.copy(messages = msgs1)
```

ChatViewModel.kt:102-105 — 同理：
```kotlin
val aiMsg = ChatMessage(role = ChatMessage.Role.ASSISTANT, content = "", isStreaming = true)
val msgs2 = msgs1.toMutableList()
msgs2.add(aiMsg)
streamingMessageId = aiMsg.id
_currentConversation.value = conv.copy(messages = msgs2)
```

ChatViewModel.kt:203-208 — applyContentBlocks 中创建新列表：
```kotlin
val newMessages = conv.messages.toMutableList()
newMessages[idx] = aiMsg.copy(...)
_currentConversation.value = conv.copy(messages = newMessages)
```

ChatViewModel.kt:225-229 — finishStreaming 中：
```kotlin
// Before:
conv.messages[idx] = aiMsg.copy(...)
_currentConversation.value = conv.copy(messages = conv.messages.toMutableList())

// After:
val newMessages = conv.messages.toMutableList()
newMessages[idx] = aiMsg.copy(
    content = if (errorMessage != null) errorMessage else aiMsg.content,
    isStreaming = false
)
_currentConversation.value = conv.copy(messages = newMessages)
```

ChatViewModel.kt:274 — selectConversation 中：
```kotlin
_currentConversation.value = c.copy(messages = messages.toMutableList())
```
改为：
```kotlin
_currentConversation.value = c.copy(messages = messages)
```

- [ ] **Step 3: 验证编译通过**

Run: `cd C:\workspace\MiMoClaw && .\gradlew.bat compileDebugKotlin 2>&1 | Select-String -Pattern "error:|BUILD"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/xiaomi/mimoclaw/feature/chat/model/ChatMessage.kt
git add app/src/main/java/com/xiaomi/mimoclaw/feature/chat/ChatViewModel.kt
git commit -m "fix: Conversation.messages 改为不可变 List"
```

---

### Task 7: LoginScreen — WebView 域名白名单验证

**Covers:** Issue #2 (Critical — 缓解措施)

**Files:**
- Modify: `app/src/main/java/com/xiaomi/mimoclaw/auth/LoginScreen.kt`

**Interfaces:**
- 无下游依赖

- [ ] **Step 1: 添加 shouldOverrideUrlLoading 域名白名单**

LoginScreen.kt:321-347 — 在 webViewClient 中添加 URL 拦截：
```kotlin
webViewClient = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url?.toString() ?: return false
        // 仅允许小米官方域名
        val allowed = url.startsWith("https://account.xiaomi.com") ||
            url.startsWith("https://aistudio.xiaomimimo.com") ||
            url.startsWith("https://mi.com") ||
            url.startsWith("https://www.mi.com")
        if (!allowed) {
            Log.w("LoginScreen", "拦截非白名单 URL: ${url.take(80)}")
            return true // 阻止加载
        }
        return false
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        // ... existing code unchanged ...
    }

    override fun onReceivedError(...) {
        // ... existing code unchanged ...
    }
}
```

- [ ] **Step 2: 验证编译通过**

Run: `cd C:\workspace\MiMoClaw && .\gradlew.bat compileDebugKotlin 2>&1 | Select-String -Pattern "error:|BUILD"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/xiaomi/mimoclaw/auth/LoginScreen.kt
git commit -m "fix: LoginScreen WebView 添加域名白名单防中间人"
```

---

### Task 8: 全局日志级别修正

**Covers:** Log.e 滥用 (附注)

**Files:**
- Modify: `app/src/main/java/com/xiaomi/mimoclaw/auth/LoginScreen.kt` (如适用)
- Modify: `app/src/main/java/com/xiaomi/mimoclaw/core/network/ClawGateway.kt`
- Modify: `app/src/main/java/com/xiaomi/mimoclaw/auth/AuthViewModel.kt`

**Interfaces:**
- 无下游依赖

- [ ] **Step 1: AuthViewModel — Log.e → Log.d**

AuthViewModel.kt:40, 48 — 正常流程日志：
```kotlin
// Before:
Log.e("AuthVM", "Cookie 有效, 直接进入")
...
Log.e("AuthVM", "需要登录")

// After:
Log.d("AuthVM", "Cookie 有效, 直接进入")
...
Log.d("AuthVM", "需要登录")
```

- [ ] **Step 2: ClawGateway — 区分正常/错误日志**

ClawGateway.kt — 以下行从 Log.e 改为 Log.d：
- 行 64: `"开始连接..."`
- 行 68: `"获取 ticket..."`
- 行 76: `"获取 ticket 成功: ..."`
- 行 99: `"使用的 ph: ..."`
- 行 102: `"尝试通过 Retrofit 获取 ticket..."`
- 行 109: `"Retrofit 获取 ticket 成功..."`
- 行 216: `"WS消息: type=$type event=$event..."`
- 行 243: `"发出AgentEvent..."`
- 行 249: `"发出ChatEvent..."`
- 行 255: `"发出SessionMessage..."`
- 行 288: `"RPC 响应..."`
- 行 474: `"发送 chat.send..."`
- 行 505: `"config.get 完整响应..."`
- 行 584: `"发送RPC..."`
- 行 64 (connect): `"开始连接..."`

保留 Log.e 的行：
- 行 71: `"获取 ticket 失败"` — 错误
- 行 82: `"连接失败"` — 错误
- 行 113: `"Retrofit 获取 ticket 失败"` — 错误
- 行 199: `"WebSocket 失败"` — 错误
- 行 224: `"解析消息失败"` — 错误

- [ ] **Step 3: 验证编译通过**

Run: `cd C:\workspace\MiMoClaw && .\gradlew.bat compileDebugKotlin 2>&1 | Select-String -Pattern "error:|BUILD"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/xiaomi/mimoclaw/auth/AuthViewModel.kt
git add app/src/main/java/com/xiaomi/mimoclaw/core/network/ClawGateway.kt
git commit -m "fix: 修正日志级别, 正常流程用 Log.d, 错误用 Log.e"
```

---

## 执行顺序

推荐顺序（按依赖和风险排序）：

1. **Task 1** — 清除硬编码凭据 (最高优先级)
2. **Task 7** — WebView 域名白名单 (安全缓解)
3. **Task 2** — 轮询超时
4. **Task 3** — MainActivity 生命周期
5. **Task 4** — AuthViewModel 线程安全
6. **Task 5** — ClawGateway 异常处理 + 复用
7. **Task 6** — MutableList → List
8. **Task 8** — 日志级别

每个 Task 独立可验证，可按需并行执行 Task 2-5。
