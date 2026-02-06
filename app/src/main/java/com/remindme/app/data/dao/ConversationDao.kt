package com.remindme.app.data.dao

import androidx.room.*
import com.remindme.app.data.entity.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY timestamp DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentConversations(limit: Int): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations ORDER BY timestamp ASC")
    fun getAllConversationsAsc(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations ORDER BY timestamp ASC")
    suspend fun getAllConversationsSync(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): Conversation?

    @Query("""
        SELECT * FROM conversations 
        WHERE message LIKE '%' || :keyword || '%' 
        ORDER BY timestamp DESC
    """)
    suspend fun searchConversations(keyword: String): List<Conversation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation): Long

    @Delete
    suspend fun deleteConversation(conversation: Conversation)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    @Query("SELECT COUNT(*) FROM conversations")
    fun getConversationCount(): Flow<Int>
}
