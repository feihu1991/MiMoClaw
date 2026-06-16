package com.xiaomi.mimoclaw.data.repository

import com.google.gson.Gson
import com.xiaomi.mimoclaw.data.local.*
import com.xiaomi.mimoclaw.data.model.*
import com.xiaomi.mimoclaw.data.remote.MiMoApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val apiService: MiMoApiService,
    private val preferencesManager: PreferencesManager,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val gson: Gson
) {
    private suspend fun getAuthToken(): String {
        val token = preferencesManager.authToken.first() ?: ""
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    // ── Conversations ──

    fun getConversations(mode: ChatMode): Flow<List<Conversation>> {
        return conversationDao.getByMode(mode.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getAllConversations(): Flow<List<Conversation>> {
        return conversationDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getConversation(id: String): Conversation? {
        return conversationDao.getById(id)?.toDomain()
    }

    suspend fun createConversation(mode: ChatMode, title: String = "New conversation"): Conversation {
        val conv = Conversation(title = title, mode = mode)
        conversationDao.insert(conv.toEntity())
        return conv
    }

    suspend fun deleteConversation(id: String) {
        messageDao.deleteByConversation(id)
        conversationDao.deleteById(id)
    }

    suspend fun updateConversationTitle(id: String, title: String) {
        conversationDao.getById(id)?.let { entity ->
            conversationDao.update(entity.copy(title = title, updatedAt = System.currentTimeMillis()))
        }
    }

    // ── Messages ──

    fun getMessages(conversationId: String): Flow<List<ChatMessage>> {
        return messageDao.getByConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    // ── Send Message with Streaming ──

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
        // Save user message
        messageDao.insert(userMessage.toEntity(conversationId))
        emit(userMessage)

        val assistantMessage = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )
        messageDao.insert(assistantMessage.toEntity(conversationId))
        emit(assistantMessage)

        try {
            val token = getAuthToken()
            val messages = messageDao.getByConversationSync(conversationId).map {
                ApiMessage(role = it.role, content = it.content)
            }
            val model = preferencesManager.selectedModel.first()
            val request = ChatRequest(
                model = model,
                messages = messages,
                stream = true,
                temperature = 0.7f,
                maxTokens = 4096
            )

            // Try SSE streaming
            try {
                val response = apiService.chatCompletionsStream(token, request)
                if (response.isSuccessful) {
                    val body = response.body() ?: throw Exception("Empty response")
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
                                        messageDao.update(updated.toEntity(conversationId))
                                        emit(updated)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    val finalMsg = assistantMessage.copy(
                        content = contentBuilder.toString().ifEmpty { "No response received." },
                        isStreaming = false
                    )
                    messageDao.update(finalMsg.toEntity(conversationId))
                    emit(finalMsg)

                    // Auto-generate title
                    val msgCount = messageDao.getByConversationSync(conversationId).size
                    if (msgCount == 2) {
                        updateConversationTitle(conversationId, content.take(30).replace("\n", " "))
                    }
                    return@flow
                }
            } catch (_: Exception) {}

            // Fallback: non-streaming
            val response = apiService.chatCompletions(token, request)
            if (response.isSuccessful) {
                val reply = response.body()?.choices?.firstOrNull()?.message?.content
                    ?: "No response received."
                val finalMsg = assistantMessage.copy(content = reply, isStreaming = false)
                messageDao.update(finalMsg.toEntity(conversationId))
                emit(finalMsg)
            } else {
                val errorMsg = assistantMessage.copy(
                    content = "Error ${response.code()}: ${response.message()}",
                    isStreaming = false
                )
                messageDao.update(errorMsg.toEntity(conversationId))
                emit(errorMsg)
            }
        } catch (e: Exception) {
            val errorMsg = assistantMessage.copy(
                content = "Network error: ${e.message ?: "Unknown error"}",
                isStreaming = false
            )
            messageDao.update(errorMsg.toEntity(conversationId))
            emit(errorMsg)
        }
    }.flowOn(Dispatchers.IO)
}

// ── Mapping Extensions ──

private fun ConversationEntity.toDomain() = Conversation(
    id = id,
    title = title,
    mode = ChatMode.valueOf(mode),
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun Conversation.toEntity() = ConversationEntity(
    id = id,
    title = title,
    mode = mode.name,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun MessageEntity.toDomain() = ChatMessage(
    id = id,
    role = MessageRole.valueOf(role.uppercase()),
    content = content,
    timestamp = timestamp,
    isStreaming = isStreaming
)

private fun ChatMessage.toEntity(conversationId: String) = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.name.lowercase(),
    content = content,
    timestamp = timestamp,
    isStreaming = isStreaming
)
