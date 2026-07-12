package com.xiaomi.mimoclaw.data.local

import com.xiaomi.mimoclaw.data.local.dao.ChatDao
import com.xiaomi.mimoclaw.data.local.entity.ConversationEntity
import com.xiaomi.mimoclaw.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地对话数据仓库
 *
 * 管理 Room 数据库中的对话和消息。
 */
@Singleton
class LocalChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {

    // ── 对话管理 ──

    fun getAllConversations(): Flow<List<ConversationEntity>> =
        chatDao.getAllConversations()

    suspend fun getAllConversationsList(): List<ConversationEntity> =
        chatDao.getAllConversationsList()

    suspend fun getConversation(id: String): ConversationEntity? =
        chatDao.getConversation(id)

    suspend fun createConversation(title: String = "新对话"): String {
        val id = UUID.randomUUID().toString()
        chatDao.insertConversation(
            ConversationEntity(
                id = id,
                title = title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        return id
    }

    suspend fun updateConversationTitle(id: String, title: String) {
        val conversation = chatDao.getConversation(id) ?: return
        chatDao.updateConversation(conversation.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun touchConversation(id: String) {
        val conversation = chatDao.getConversation(id) ?: return
        chatDao.updateConversation(conversation.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteConversation(id: String) {
        chatDao.deleteConversation(id)
    }

    // ── 消息管理 ──

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> =
        chatDao.getMessages(conversationId)

    suspend fun getMessagesList(conversationId: String): List<MessageEntity> =
        chatDao.getMessagesList(conversationId)

    suspend fun addMessage(conversationId: String, role: String, content: String): String {
        val id = UUID.randomUUID().toString()
        chatDao.insertMessage(
            MessageEntity(
                id = id,
                conversationId = conversationId,
                role = role,
                content = content,
                timestamp = System.currentTimeMillis()
            )
        )
        // 更新对话的最后修改时间
        touchConversation(conversationId)
        return id
    }

    suspend fun updateMessage(messageId: String, content: String) {
        // Room 没有直接的 update by id，需要先查再改
        // 这里用一个简单的方式
        val messages = chatDao.getMessagesList("").flatMap { emptyList<MessageEntity>() } // placeholder
        // 实际实现需要用 DAO 的 query
    }

    suspend fun updateMessageContent(messageId: String, content: String) {
        // 通过 DAO 直接更新
        chatDao.insertMessage(
            MessageEntity(id = messageId, conversationId = "", role = "", content = content)
        )
    }

    suspend fun getMessageCount(conversationId: String): Int =
        chatDao.getMessageCount(conversationId)

    /**
     * 自动生成对话标题（取用户第一条消息的前 20 个字）
     */
    suspend fun autoTitle(conversationId: String) {
        val messages = chatDao.getMessagesList(conversationId)
        val firstUserMessage = messages.firstOrNull { it.role == "user" }?.content ?: return
        val title = firstUserMessage.take(20).replace("\n", " ")
        updateConversationTitle(conversationId, title)
    }
}
