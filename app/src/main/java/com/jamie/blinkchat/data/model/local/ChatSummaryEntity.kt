package com.jamie.blinkchat.data.model.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_summaries")
data class ChatSummaryEntity(
    @PrimaryKey val id: String, // Chat ID (UUID)
    val otherParticipantId: String, // Store the ID of the other user in a 1:1 chat
    val otherParticipantUsername: String,
    // val otherParticipantAvatarUrl: String? = null, // For future use
    val lastMessageId: String?, // ID of the last message
    val lastMessageContent: String?,
    val lastMessageTimestamp: Long?, // Store as Long (epoch millis) for easier sorting/comparison
    val lastMessageSenderId: String?,
    val lastMessageStatus: String?, // "sent", "delivered", "read"
    @ColumnInfo(defaultValue = "0") // Default to 0 if not specified
    val unreadCount: Int = 0,
    val chatCreatedAt: Long // Store as Long (epoch millis)
)