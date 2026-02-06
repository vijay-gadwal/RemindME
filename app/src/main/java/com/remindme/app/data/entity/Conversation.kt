package com.remindme.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageType {
    USER_TEXT,
    USER_VOICE,
    ASSISTANT_RESPONSE,
    SYSTEM_NOTIFICATION,
    CONTEXT_UPDATE
}

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val message: String,
    val messageType: MessageType = MessageType.USER_TEXT,
    val isFromUser: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val relatedTaskIds: String? = null,
    val relatedGoalIds: String? = null,
    val intent: String? = null,
    val confidence: Float? = null
)
