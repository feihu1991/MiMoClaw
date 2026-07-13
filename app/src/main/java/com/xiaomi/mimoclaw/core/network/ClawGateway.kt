package com.xiaomi.mimoclaw.core.network

import android.util.Log
import android.webkit.CookieManager
import com.xiaomi.mimoclaw.BuildConfig
import com.xiaomi.mimoclaw.auth.AuthRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MiMo Claw WebSocket Gateway 客户端
 *
 * 协议: OpenClaw JSON-RPC over WebSocket
 * 流程: getTicket → connect WS → connect.challenge → connect → sessions.subscribe → chat.send
 */
@Singleton
class ClawGateway @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val TAG = "ClawGateway"
        private val WS_BASE get() = BuildConfig.WS_BASE_URL
        private val REST_BASE get() = BuildConfig.API_BASE_URL
    }

    // ── 连接状态 ──
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ── 事件流 (供 UI 消费) ──
    private val _events = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 64)
    val events: Flow<GatewayEvent> = _events.asSharedFlow()

    // ── 内部状态 ──
    private var webSocket: WebSocket? = null
    private var ticket: String? = null
    private var connId: String? = null
    private var sessionKey: String = "agent:main:main"  // 默认 session
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JSONObject?>>()
    private var reconnectJob: Job? = null
    private val socketLock = Any()
    private var connectionGeneration = 0L
    private var connectionReady: CompletableDeferred<Unit>? = null
    private val wsClient = okHttpClient.newBuilder()
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() +
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Coroutine 未捕获异常", throwable)
        })

    private fun debug(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    // ── 连接 ──

    /**
     * 启动连接: 获取 ticket → 建立 WebSocket → RPC 握手
     */
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        disconnect()
        val generation = synchronized(socketLock) {
            connectionGeneration += 1
            connectionGeneration
        }
        try {
            debug { "开始连接" }
            _connectionState.value = ConnectionState.CONNECTING

            // Step 1: 获取 WebSocket ticket
            val ticketResult = fetchTicket()
            if (ticketResult.isFailure) {
                Log.e(TAG, "获取 ticket 失败: ${ticketResult.exceptionOrNull()?.message}")
                _connectionState.value = ConnectionState.DISCONNECTED
                return@withContext Result.failure(ticketResult.exceptionOrNull()!!)
            }
            if (!isActiveGeneration(generation)) {
                return@withContext Result.failure(CancellationException("连接已被新的请求替代"))
            }
            ticket = ticketResult.getOrThrow()

            // Step 2: 建立 WebSocket
            val ready = CompletableDeferred<Unit>()
            synchronized(socketLock) {
                if (generation != connectionGeneration) {
                    return@withContext Result.failure(CancellationException("连接已被新的请求替代"))
                }
                connectionReady = ready
                webSocket = createWebSocket(ticket!!, generation, ready)
            }
            withTimeout(30_000) { ready.await() }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "连接失败", e)
            if (isActiveGeneration(generation)) disconnect()
            Result.failure(e)
        }
    }

    private fun isActiveGeneration(generation: Long): Boolean =
        synchronized(socketLock) { generation == connectionGeneration }

    private suspend fun fetchTicket(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 从 CookieManager 获取 ph 值 (登录态由登录页写入)
            val cookies = CookieManager.getInstance().getCookie(REST_BASE) ?: ""
            val ph = extractPhFromCookies(cookies)

            if (ph.isNullOrBlank()) {
                Log.w(TAG, "CookieManager 无有效 Cookie, 请先登录")
                return@withContext Result.failure(Exception("未登录, 请先在登录页完成小米账号认证"))
            }

            // 先尝试通过 Retrofit (带 Cookie interceptor，ph 已在 Cookie 中)
            val response = authRepository.getWsTicket()
            if (response.isSuccessful) {
                val body = response.body()
                // 兼容两种结构: { data: { ticket: "..." } } 或 { ticket: "..." }
                val ticket = body?.data?.ticket ?: body?.ticket
                if (!ticket.isNullOrBlank()) {
                    return@withContext Result.success(ticket)
                }
            }
            Log.w(TAG, "Retrofit 获取 ticket 失败: HTTP ${response.code()}")

            // Fallback: 原生 OkHttp 请求（ph 通过 Cookie 传输，不暴露在 URL 中）
            val url = "$REST_BASE/open-apis/user/ws/ticket"
            val req = Request.Builder().url(url).get()
                .header("Cookie", cookies)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/137.0.0.0 Mobile Safari/537.36")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                debug { "Fallback ticket 响应: HTTP ${resp.code}" }
                if (resp.isSuccessful) {
                    val json = JSONObject(body)
                    // 兼容两种结构: { data: { ticket: "..." } } 或 { ticket: "..." }
                    val t = json.optJSONObject("data")?.optString("ticket")
                        ?: json.optString("ticket", "")
                    if (t.isNotBlank()) return@withContext Result.success(t)
                }
                return@withContext Result.failure(Exception("获取 ticket 失败: HTTP ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("获取 ticket 异常: ${e.message}"))
        }
    }

    /**
     * 从 cookie 字符串中提取 xiaomichatbot_ph 值 (去除两端引号)
     */
    private fun extractPhFromCookies(cookies: String): String? {
        return cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("xiaomichatbot_ph=") }
            ?.substringAfter("xiaomichatbot_ph=")
            ?.trim('"')
    }

    private fun createWebSocket(
        ticket: String,
        generation: Long,
        ready: CompletableDeferred<Unit>
    ): WebSocket {
        val wsUrl = "$WS_BASE?ticket=$ticket"
        val cookies = CookieManager.getInstance().getCookie(REST_BASE) ?: ""

        val request = Request.Builder()
            .url(wsUrl)
            .header("Origin", REST_BASE)
            .header("Cookie", cookies)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/137.0.0.0 Mobile Safari/537.36")
            .build()

        return wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(socketLock) {
                    if (generation == connectionGeneration) this@ClawGateway.webSocket = webSocket
                }
                debug { "WebSocket transport 已连接" }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (isActiveGeneration(generation)) handleMessage(text, generation, ready)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                debug { "WebSocket closing: $code" }
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (isActiveGeneration(generation)) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    ready.completeExceptionally(Exception("连接已关闭 ($code)"))
                    failPendingRequests(Exception("连接已关闭"))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (isActiveGeneration(generation)) {
                    Log.e(TAG, "WebSocket 失败: ${t.message}")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    ready.completeExceptionally(t)
                    failPendingRequests(t)
                }
            }
        })
    }

    // ── 消息处理 ──

    private fun handleMessage(
        text: String,
        generation: Long,
        ready: CompletableDeferred<Unit>
    ) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type", "")
            val event = json.optString("event", "")

            debug { "WS消息: type=$type event=$event" }

            when (type) {
                "event" -> handleEvent(json, generation, ready)
                "res" -> handleResponse(json)
                else -> Log.w(TAG, "未知消息类型: $type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 WebSocket 消息失败: ${e.message}")
        }
    }

    private fun handleEvent(
        json: JSONObject,
        generation: Long,
        ready: CompletableDeferred<Unit>
    ) {
        val event = json.optString("event", "")
        val payload = json.optJSONObject("payload")

        when (event) {
            "connect.challenge" -> {
                // 收到 challenge 后发送 connect 请求
                scope.launch {
                    val result = doConnect()
                    if (!isActiveGeneration(generation)) return@launch
                    result.onSuccess {
                        _connectionState.value = ConnectionState.CONNECTED
                        ready.complete(Unit)
                        scope.launch { subscribeSessions() }
                        scope.launch { listSessions() }
                    }.onFailure {
                        ready.completeExceptionally(it)
                    }
                }
            }
            "agent" -> {
                // HAR 真实格式: 区分 stream 类型
                val stream = payload?.optString("stream", "")
                when (stream) {
                    "lifecycle" -> {
                        // 生命周期事件: phase="start"/"end"
                        val phase = payload?.optJSONObject("data")?.optString("phase", "")
                        debug { "agent lifecycle: $phase" }
                    }
                    "assistant" -> {
                        // 助手文本增量: data.text=完整文本, data.delta=增量
                        val data = payload?.optJSONObject("data")
                        val delta = data?.optString("delta", "") ?: ""
                        val blocks = if (delta.isNotBlank()) listOf(ContentBlock.Text(delta)) else emptyList()
                        val eventSessionKey = extractEventSessionKey(payload)
                        scope.launch { _events.emit(GatewayEvent.AgentEvent(eventSessionKey, blocks)) }
                    }
                    else -> {
                        debug { "agent stream: $stream" }
                    }
                }
            }
            "chat" -> {
                // HAR 真实格式: 区分 state 类型
                val state = payload?.optString("state", "")
                when (state) {
                    "delta" -> {
                        // 流式增量: deltaText=增量文本
                        val deltaText = payload?.optString("deltaText", "") ?: ""
                        val blocks = if (deltaText.isNotBlank()) listOf(ContentBlock.Text(deltaText)) else emptyList()
                        val eventSessionKey = extractEventSessionKey(payload)
                        scope.launch { _events.emit(GatewayEvent.ChatEvent(eventSessionKey, blocks)) }
                    }
                    "final" -> {
                        // 最终消息: message 包含完整内容
                        val blocks = parseChatBlocks(payload)
                        val eventSessionKey = extractEventSessionKey(payload)
                        scope.launch { _events.emit(GatewayEvent.ChatEvent(eventSessionKey, blocks)) }
                    }
                    else -> {
                        debug { "chat state: $state" }
                    }
                }
            }
            "session.message" -> {
                // session 消息: message.content 包含完整的 thinking + text 块
                val blocks = parseSessionMessageBlocks(payload)
                val eventSessionKey = extractEventSessionKey(payload)
                scope.launch { _events.emit(GatewayEvent.SessionMessage(eventSessionKey, blocks)) }
            }
            "session.operation" -> {
                debug { "session.operation" }
            }
            "session.tool" -> {
                val eventSessionKey = extractEventSessionKey(payload)
                val tool = payload?.optJSONObject("tool") ?: payload
                val name = tool?.optString("name")
                    ?.takeIf { it.isNotBlank() }
                    ?: tool?.optString("toolName")?.takeIf { it.isNotBlank() }
                    ?: "工具调用"
                val status = tool?.optString("status")?.takeIf { it.isNotBlank() }
                    ?: tool?.optString("state")?.takeIf { it.isNotBlank() }
                    ?: "执行中"
                scope.launch { _events.emit(GatewayEvent.ToolEvent(eventSessionKey, name, status)) }
            }
            "sessions.changed" -> {
                debug { "sessions.changed" }
            }
            "health" -> {
                // 周期性健康检查, 忽略
            }
            "tick" -> {
                // 周期性 tick, 忽略
            }
            "heartbeat" -> {
                debug { "heartbeat" }
            }
            else -> {
                debug { "事件: $event" }
            }
        }
    }

    private fun handleResponse(json: JSONObject) {
        val id = json.optString("id", "")
        val ok = json.optBoolean("ok", false)
        val payload = json.optJSONObject("payload")
        val error = json.optString("error", json.optString("message", ""))

        debug { "RPC 响应: id=$id ok=$ok hasError=${error.isNotBlank()}" }

        val deferred = pendingRequests.remove(id)
        if (deferred != null) {
            if (ok) {
                deferred.complete(payload ?: JSONObject())
            } else {
                val errMsg = if (error.isNotBlank()) error else (payload?.toString() ?: "未知错误")
                deferred.completeExceptionally(Exception("RPC 错误: $errMsg"))
            }
        } else {
            debug { "未匹配的响应: id=$id" }
        }
    }

    /**
     * 解析 chat 事件的内容块
     * chat 事件: deltaText (增量文本) — 不使用 message.content (那是完整文本, 容易重复)
     */
    private fun parseChatBlocks(payload: JSONObject?): List<ContentBlock> {
        if (payload == null) return emptyList()
        val blocks = mutableListOf<ContentBlock>()

        // deltaText 是流式增量
        val deltaText = payload.optString("deltaText", "")
        if (deltaText.isNotBlank()) {
            blocks.add(ContentBlock.Text(deltaText))
        }
        return blocks
    }

    /**
     * 解析 session.message 事件的内容块
     * message.content 包含 thinking + text 完整块
     */
    private fun parseSessionMessageBlocks(payload: JSONObject?): List<ContentBlock> {
        if (payload == null) return emptyList()
        val blocks = mutableListOf<ContentBlock>()

        val message = payload.optJSONObject("message")
        val content = message?.optJSONArray("content")
        if (content != null) {
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                when (block.optString("type", "")) {
                    "thinking" -> blocks.add(ContentBlock.Thinking(block.optString("thinking", "")))
                    "text" -> blocks.add(ContentBlock.Text(block.optString("text", "")))
                    "tool" -> blocks.add(ContentBlock.Tool(block.optString("name", "工具调用"), block.optString("status", "执行中")))
                }
            }
        }
        return blocks
    }

    private fun parseContentBlocks(payload: JSONObject?): List<ContentBlock> {
        if (payload == null) return emptyList()
        val content = payload.optJSONArray("content")
        if (content == null) return emptyList()
        val blocks = mutableListOf<ContentBlock>()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            val blockType = block.optString("type", "")
            when (blockType) {
                "thinking" -> blocks.add(ContentBlock.Thinking(block.optString("thinking", "")))
                "text" -> blocks.add(ContentBlock.Text(block.optString("text", "")))
                else -> {
                    val text = block.optString("text", block.optString("content", ""))
                    if (text.isNotBlank()) blocks.add(ContentBlock.Text(text))
                }
            }
        }
        return blocks
    }

    private fun extractEventSessionKey(payload: JSONObject?): String? {
        if (payload == null) return null
        return payload.optString("sessionKey").takeIf { it.isNotBlank() }
            ?: payload.optJSONObject("data")
                ?.optString("sessionKey")
                ?.takeIf { it.isNotBlank() }
            ?: payload.optJSONObject("message")
                ?.optString("sessionKey")
                ?.takeIf { it.isNotBlank() }
    }

    // ── RPC 请求 ──

    private suspend fun doConnect(): Result<Unit> {
        val id = UUID.randomUUID().toString()
        val params = JSONObject().apply {
            put("minProtocol", 3)
            put("maxProtocol", 4)
            put("client", JSONObject().apply {
                put("id", "cli")
                put("version", "mimo-claw-android")
                put("platform", "Android")
                put("mode", "cli")
            })
            put("role", "operator")
            put("scopes", org.json.JSONArray(listOf(
                "operator.admin", "operator.read", "operator.write",
                "operator.approvals", "operator.pairing"
            )))
            put("caps", org.json.JSONArray(listOf("tool-events")))
            put("userAgent", "Mozilla/5.0 (Linux; Android 14) Chrome/137.0.0.0 Mobile Safari/537.36")
            put("locale", "zh-CN")
        }

        val result = sendRpc(id, "connect", params)
        return result.map { payload ->
            connId = payload?.optJSONObject("server")?.optString("connId")
            debug { "Gateway connect 握手成功" }
        }
    }

    private suspend fun subscribeSessions() {
        val id = UUID.randomUUID().toString()
        val result = sendRpc(id, "sessions.subscribe", JSONObject())
        result.onSuccess {
            debug { "sessions.subscribe 成功" }
        }.onFailure { e ->
            Log.e(TAG, "sessions.subscribe 失败", e)
        }
    }

    private suspend fun listSessions() {
        val id = UUID.randomUUID().toString()
        val params = JSONObject().apply {
            put("includeGlobal", true)
            put("includeUnknown", false)
            put("limit", 120)
        }
        val result = sendRpc(id, "sessions.list", params)
        result.onSuccess { payload ->
            val sessions = payload?.optJSONArray("sessions")
            val count = sessions?.length() ?: 0
            debug { "sessions.list 成功, $count 个 session" }

            val sessionList = mutableListOf<SessionInfo>()
            if (sessions != null) {
                for (i in 0 until sessions.length()) {
                    val s = sessions.getJSONObject(i)
                    sessionList.add(SessionInfo(
                        key = s.optString("key", ""),
                        sessionId = s.optString("sessionId", ""),
                        title = extractTitle(s),
                        model = s.optString("model", "mimo-v2.5-pro"),
                        updatedAt = s.optLong("updatedAt", 0)
                    ))
                }
            }
            scope.launch {
                _events.emit(GatewayEvent.SessionList(sessionList))
            }
        }.onFailure { e ->
            Log.e(TAG, "sessions.list 失败", e)
        }
    }

    private fun extractTitle(session: JSONObject): String {
        // 尝试从 session 的 origin 或 messages 中提取标题
        val key = session.optString("key", "")
        return when {
            key.contains("dashboard:") -> key.substringAfterLast("dashboard:", "")
                .take(8).ifEmpty { "新对话" }
            key.contains("cron:") -> "定时任务"
            key == "agent:main:main" -> "主会话"
            else -> "对话 ${key.takeLast(8)}"
        }
    }

    /**
     * 发送聊天消息
     * @return 返回该请求的 requestId, 用于取消
     */
    suspend fun sendChatMessage(
        message: String,
        model: String = "mimo-v2.5-pro",
        sessionKey: String = this.sessionKey
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val requestId = UUID.randomUUID().toString()

        // HAR 真实格式: idempotencyKey 为纯 UUID, deliver 为 false
        val params = JSONObject().apply {
            put("sessionKey", sessionKey)
            put("message", message)
            put("deliver", false)
            put("idempotencyKey", requestId)
        }

        debug { "发送 chat.send: session=$sessionKey model=$model" }
        val result = sendRpc(requestId, "chat.send", params)
        result.map { }
    }

    /**
     * 中止当前回复
     */
    suspend fun abortChat(sessionKey: String = this.sessionKey) {
        val id = UUID.randomUUID().toString()
        sendRpc(id, "chat.abort", JSONObject().apply {
            put("sessionKey", sessionKey)
        })
    }

    /**
     * 更新会话模型
     * 先获取 config baseHash, 再 patch
     */
    suspend fun setSessionModel(
        sessionKey: String = this.sessionKey,
        model: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val id1 = UUID.randomUUID().toString()
        // Step 1: 获取当前 config 和 baseHash
        val getResult = sendRpc(id1, "config.get", JSONObject())
        if (getResult.isFailure) {
            return@withContext Result.failure(getResult.exceptionOrNull()!!)
        }
        val configPayload = getResult.getOrNull()
        val baseHash = configPayload?.optString("baseHash") ?: ""
        debug { "config.get 完成, hasBaseHash=${baseHash.isNotBlank()}" }

        // Step 2: patch config
        val id2 = UUID.randomUUID().toString()
        val raw = JSONObject().apply {
            put("sessions", JSONObject().apply {
                put(sessionKey, JSONObject().apply { put("model", model) })
            })
        }.toString()
        val params = JSONObject().apply {
            put("raw", raw)
            if (baseHash.isNotBlank()) put("baseHash", baseHash)
        }
        val patchResult = sendRpc(id2, "config.patch", params)
        if (patchResult.isSuccess) {
            debug { "模型已切换: session=$sessionKey model=$model" }
            Result.success(Unit)
        } else {
            Result.failure(patchResult.exceptionOrNull() ?: Exception("config.patch 失败"))
        }
    }

    /**
     * 获取聊天历史
     */
    suspend fun getChatHistory(
        sessionKey: String,
        limit: Int = 200
    ): Result<List<HistoryMessage>> = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val params = JSONObject().apply {
            put("sessionKey", sessionKey)
            put("limit", limit)
        }
        val result = sendRpc(id, "chat.history", params)
        result.map { payload ->
            val messages = payload?.optJSONArray("messages") ?: org.json.JSONArray()
            val list = mutableListOf<HistoryMessage>()
            for (i in 0 until messages.length()) {
                val msg = messages.getJSONObject(i)
                val role = msg.optString("role", "user")
                val content = msg.opt("content")
                var thinking = ""
                val tools = mutableListOf<HistoryToolCall>()
                val contentStr = when (content) {
                    is org.json.JSONArray -> {
                        val parts = mutableListOf<String>()
                        for (j in 0 until content.length()) {
                            val block = content.getJSONObject(j)
                            when (block.optString("type")) {
                                "text" -> parts.add(block.optString("text", ""))
                                "thinking" -> thinking += block.optString("thinking", block.optString("text", ""))
                                "tool", "tool_use", "toolUse" -> tools.add(HistoryToolCall(
                                    name = block.optString("name", block.optString("toolName", "工具调用")),
                                    status = block.optString("status", block.optString("state", "已完成"))
                                ))
                            }
                        }
                        parts.joinToString("")
                    }
                    is String -> content
                    else -> content?.toString() ?: ""
                }
                list.add(HistoryMessage(role = role, content = contentStr, thinking = thinking, tools = tools))
            }
            list
        }
    }

    // ── RPC 核心 ──

    private suspend fun sendRpc(
        id: String,
        method: String,
        params: JSONObject
    ): Result<JSONObject?> {
        val deferred = CompletableDeferred<JSONObject?>()
        pendingRequests[id] = deferred

        val request = JSONObject().apply {
            put("type", "req")
            put("id", id)
            put("method", method)
            put("params", params)
        }

        val text = request.toString()
        val sent = webSocket?.send(text) ?: false
        debug { "发送 RPC: id=$id method=$method sent=$sent" }
        if (!sent) {
            pendingRequests.remove(id)
            return Result.failure(Exception("WebSocket 未连接"))
        }

        return try {
            withTimeout(120_000) {
                val payload = deferred.await()
                Result.success(payload)
            }
        } catch (e: Exception) {
            pendingRequests.remove(id)
            Result.failure(e)
        }
    }

    // ── 会话切换 ──

    fun setSessionKey(key: String) {
        sessionKey = key
    }

    fun createDashboardSessionKey(): String = "agent:main:dashboard:${UUID.randomUUID()}"

    // ── 断开 ──

    fun disconnect() {
        val socket = synchronized(socketLock) {
            connectionGeneration += 1
            reconnectJob?.cancel()
            reconnectJob = null
            connectionReady?.completeExceptionally(CancellationException("连接已断开"))
            connectionReady = null
            val current = webSocket
            webSocket = null
            current
        }
        socket?.close(1000, "用户断开")
        failPendingRequests(CancellationException("连接已断开"))
        ticket = null
        connId = null
        sessionKey = "agent:main:main"
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun failPendingRequests(cause: Throwable) {
        pendingRequests.values.forEach { it.completeExceptionally(cause) }
        pendingRequests.clear()
    }
}

// ── 数据模型 ──

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

sealed class GatewayEvent {
    data class AgentEvent(val sessionKey: String?, val blocks: List<ContentBlock>) : GatewayEvent()
    data class ChatEvent(val sessionKey: String?, val blocks: List<ContentBlock>) : GatewayEvent()
    data class SessionMessage(val sessionKey: String?, val blocks: List<ContentBlock>) : GatewayEvent()
    data class ToolEvent(val sessionKey: String?, val name: String, val status: String) : GatewayEvent()
    data class SessionList(val sessions: List<SessionInfo>) : GatewayEvent()
}

sealed class ContentBlock {
    data class Thinking(val text: String) : ContentBlock()
    data class Text(val text: String) : ContentBlock()
    data class Tool(val name: String, val status: String) : ContentBlock()
}

data class SessionInfo(
    val key: String,
    val sessionId: String,
    val title: String,
    val model: String,
    val updatedAt: Long
)

data class HistoryMessage(
    val role: String,
    val content: String,
    val thinking: String = "",
    val tools: List<HistoryToolCall> = emptyList()
)

data class HistoryToolCall(val name: String, val status: String)
