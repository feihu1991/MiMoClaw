package com.xiaomi.mimoclaw.data.chat

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ResponseBody
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 对话仓库 - 处理与 MiMo API 的通信
 *
 * 核心职责：
 * 1. SSE 流式解析 → Flow<StreamChunk>
 * 2. 对话历史管理
 * 3. 错误处理与重试
 */
@Singleton
class ChatRepository @Inject constructor(
    private val chatApi: ChatApi,
    private val gson: Gson
) {

    /**
     * 发送消息并获取流式响应
     *
     * 返回 Flow，每个元素是一个 StreamChunk（包含增量文本）。
     * Flow 完成时表示整个响应结束。
     */
    fun sendMessageStream(
        messages: List<ChatMessage>,
        model: String? = null
    ): Flow<StreamEvent> = flow {
        val request = ChatRequest(
            messages = messages,
            stream = true,
            model = model
        )

        try {
            val response = chatApi.chatCompletionsStream(request)

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string() ?: "未知错误"
                emit(StreamEvent.Error("请求失败 (${response.code()}): $errorBody"))
                return@flow
            }

            val body = response.body() ?: run {
                emit(StreamEvent.Error("响应体为空"))
                return@flow
            }

            parseSSEStream(body)
        } catch (e: Exception) {
            emit(StreamEvent.Error("网络错误: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 解析 SSE 流
     *
     * SSE 格式：
     * ```
     * data: {"id":"...","choices":[{"delta":{"content":"你好"}}]}
     * data: {"id":"...","choices":[{"delta":{"content":"！"}}]}
     * data: [DONE]
     * ```
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamEvent>.parseSSEStream(
        body: ResponseBody
    ) {
        val reader = BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))
        var fullContent = StringBuilder()

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line?.trim() ?: continue

                // 跳过空行和注释
                if (trimmed.isEmpty() || trimmed.startsWith(":")) continue

                // 处理 data: 行
                if (trimmed.startsWith("data:")) {
                    val data = trimmed.removePrefix("data:").trim()

                    // 流结束标记
                    if (data == "[DONE]") {
                        emit(StreamEvent.Done(fullContent.toString()))
                        return
                    }

                    // 解析 JSON chunk
                    try {
                        val chunk = gson.fromJson(data, ChatStreamChunk::class.java)
                        val delta = chunk.choices?.firstOrNull()?.delta
                        val content = delta?.content

                        if (!content.isNullOrEmpty()) {
                            fullContent.append(content)
                            emit(StreamEvent.Delta(content))
                        }

                        // 检查是否有 finish_reason
                        val finishReason = chunk.choices?.firstOrNull()?.finishReason
                        if (finishReason != null && finishReason != "null") {
                            emit(StreamEvent.Done(fullContent.toString()))
                            return
                        }
                    } catch (e: Exception) {
                        // JSON 解析失败，跳过这行
                        emit(StreamEvent.Debug("JSON parse error: $data"))
                    }
                }
            }

            // 读取结束但没收到 [DONE]
            if (fullContent.isNotEmpty()) {
                emit(StreamEvent.Done(fullContent.toString()))
            } else {
                emit(StreamEvent.Error("流式响应为空"))
            }
        } finally {
            reader.close()
            body.close()
        }
    }

    /**
     * 获取对话列表
     */
    suspend fun getConversations(page: Int = 1): Result<List<ConversationItem>> {
        return try {
            val response = chatApi.getConversations(page = page)
            if (response.isSuccessful && response.body()?.data != null) {
                Result.success(response.body()!!.data!!.conversations.orEmpty())
            } else {
                Result.failure(Exception("获取对话列表失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取对话详情
     */
    suspend fun getConversationDetail(conversationId: String): Result<ConversationDetailData?> {
        return try {
            val response = chatApi.getConversationDetail(conversationId)
            if (response.isSuccessful) {
                Result.success(response.body()?.data)
            } else {
                Result.failure(Exception("获取对话详情失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 删除对话
     */
    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        return try {
            val response = chatApi.deleteConversation(conversationId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("删除对话失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * SSE 流事件
 */
sealed class StreamEvent {
    /** 增量文本 */
    data class Delta(val text: String) : StreamEvent()

    /** 流结束 */
    data class Done(val fullContent: String) : StreamEvent()

    /** 错误 */
    data class Error(val message: String) : StreamEvent()

    /** 调试信息（仅 debug 模式） */
    data class Debug(val message: String) : StreamEvent()
}
