package com.remindme.app.repository

import com.remindme.app.data.dao.ConversationDao
import com.remindme.app.data.entity.Conversation
import com.remindme.app.data.entity.MessageType
import kotlinx.coroutines.flow.Flow

class ConversationRepository(
    private val conversationDao: ConversationDao
) {
    fun getAllConversations(): Flow<List<Conversation>> = conversationDao.getAllConversationsAsc()
    fun getRecentConversations(limit: Int = 50): Flow<List<Conversation>> = conversationDao.getRecentConversations(limit)

    suspend fun addUserMessage(message: String, messageType: MessageType = MessageType.USER_TEXT): Long {
        return conversationDao.insertConversation(
            Conversation(
                message = message,
                messageType = messageType,
                isFromUser = true
            )
        )
    }

    suspend fun addAssistantResponse(
        message: String,
        relatedTaskIds: String? = null,
        relatedGoalIds: String? = null
    ): Long {
        return conversationDao.insertConversation(
            Conversation(
                message = message,
                messageType = MessageType.ASSISTANT_RESPONSE,
                isFromUser = false,
                relatedTaskIds = relatedTaskIds,
                relatedGoalIds = relatedGoalIds
            )
        )
    }

    suspend fun searchConversations(keyword: String): List<Conversation> =
        conversationDao.searchConversations(keyword)

    suspend fun clearHistory() = conversationDao.deleteAllConversations()
}
