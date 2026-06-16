package com.xiaomi.mimoclaw.data.repository

import com.google.gson.Gson
import com.xiaomi.mimoclaw.data.local.PreferencesManager
import com.xiaomi.mimoclaw.data.model.*
import com.xiaomi.mimoclaw.data.remote.MiMoApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val apiService: MiMoApiService,
    private val preferencesManager: PreferencesManager,
    private val gson: Gson
) {
    private val conversations = mutableListOf<Conversation>()
    private val conversationMessages = mutableMapOf<String, MutableList<ChatMessage>>()

    private suspend fun getAuthToken(): String {
        val token = preferencesManager.authToken.first() ?: ""
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    fun getConversations(mode: ChatMode): List<Conversation> {
        return conversations.filter { it.mode == mode }.sortedByDescending { it.updatedAt }
    }

    fun getConversation(id: String): Conversation? = conversations.find { it.id == id }

    fun createConversation(mode: ChatMode, title: String = "New conversation"): Conversation {
        val conv = Conversation(title = title, mode = mode)
        conversations.add(conv)
        conversationMessages[conv.id] = mutableListOf()
        return conv
    }

    fun deleteConversation(id: String) {
        conversations.removeAll { it.id == id }
        conversationMessages.remove(id)
    }

    fun getMessages(conversationId: String): List<ChatMessage> {
        return conversationMessages[conversationId] ?: emptyList()
    }

    /**
     * Send message with SSE streaming support.
     * Falls back to non-streaming if streaming fails.
     */
    fun sendMessage(
        conversationId: String,
        content: String,
        attachments: List<Attachment> = emptyList()
    ): Flow<ChatMessage> = flow {
        val userMessage = ChatMessage(
            role = MessageRole.USER,
            content = content,
            attachments = attachments
        )
        conversationMessages.getOrPut(conversationId) { mutableListOf() }.add(userMessage)
        emit(userMessage)

        val assistantMessage = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )
        conversationMessages[conversationId]!!.add(assistantMessage)
        emit(assistantMessage)

        try {
            val token = getAuthToken()
            val messages = conversationMessages[conversationId]!!.map {
                ApiMessage(role = it.role.name.lowercase(), content = it.content)
            }
            val model = preferencesManager.selectedModel.first()
            val request = ChatRequest(
                model = model,
                messages = messages,
                stream = true,
                temperature = 0.7f,
                maxTokens = 4096
            )

            // Try SSE streaming first
            try {
                val response = apiService.chatCompletionsStream(token, request)
                if (response.isSuccessful) {
                    val body = response.body() ?: throw Exception("Empty response body")
                    val reader = body.byteStream().bufferedReader()
                    val contentBuilder = StringBuilder()

                    reader.useLines { lines ->
                        lines.forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.startsWith("data: ") && trimmed != "data: [DONE]") {
                                try {
                                    val json = trimmed.removePrefix("data: ")
                                    val chunk = gson.fromJson(json, ChatResponse::class.java)
                                    val delta = chunk.choices?.firstOrNull()?.delta?.content
                                    if (delta != null) {
                                        contentBuilder.append(delta)
                                        val updated = assistantMessage.copy(
                                            content = contentBuilder.toString(),
                                            isStreaming = true
                                        )
                                        val msgs = conversationMessages[conversationId]!!
                                        msgs[msgs.lastIndex] = updated
                                        emit(updated)
                                    }
                                } catch (_: Exception) { /* skip malformed chunks */ }
                            }
                        }
                    }

                    // Final message
                    val finalMsg = assistantMessage.copy(
                        content = contentBuilder.toString().ifEmpty { "No response received." },
                        isStreaming = false
                    )
                    val msgs = conversationMessages[conversationId]!!
                    msgs[msgs.lastIndex] = finalMsg
                    emit(finalMsg)

                    // Auto-generate title from first exchange
                    if (conversationMessages[conversationId]!!.size == 2) {
                        val title = content.take(30).replace("\n", " ")
                        updateConversationTitle(conversationId, title)
                    }
                    return@flow
                }
            } catch (e: Exception) {
                // Streaming failed, fall through to non-streaming
            }

            // Fallback: non-streaming
            val response = apiService.chatCompletions(token, request)
            if (response.isSuccessful) {
                val result = response.body()
                val reply = result?.choices?.firstOrNull()?.message?.content
                    ?: "No response received."
                val finalMsg = assistantMessage.copy(content = reply, isStreaming = false)
                val msgs = conversationMessages[conversationId]!!
                msgs[msgs.lastIndex] = finalMsg
                emit(finalMsg)
            } else {
                val errorMsg = assistantMessage.copy(
                    content = "Error ${response.code()}: ${response.message()}",
                    isStreaming = false
                )
                val msgs = conversationMessages[conversationId]!!
                msgs[msgs.lastIndex] = errorMsg
                emit(errorMsg)
            }
        } catch (e: Exception) {
            val errorMsg = assistantMessage.copy(
                content = "Network error: ${e.message ?: "Unknown error"}",
                isStreaming = false
            )
            val msgs = conversationMessages[conversationId]!!
            msgs[msgs.lastIndex] = errorMsg
            emit(errorMsg)
        }
    }.flowOn(Dispatchers.IO)

    fun updateConversationTitle(conversationId: String, title: String) {
        conversations.indexOfFirst { it.id == conversationId }.takeIf { it >= 0 }?.let { idx ->
            conversations[idx] = conversations[idx].copy(title = title, updatedAt = System.currentTimeMillis())
        }
    }
}
