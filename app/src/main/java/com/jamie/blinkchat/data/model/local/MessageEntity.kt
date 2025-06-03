package com.jamie.blinkchat.data.model.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSummaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE // If a chat is deleted, delete its messages
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["senderId"],
            onDelete = ForeignKey.SET_NULL // If sender is deleted, keep message but nullify senderId
        )
        // Potentially a foreign key for receiverId if you store it and want to enforce it
    ],
    indices = [Index(value = ["chatId"]), Index(value = ["senderId"]), Index(value = ["clientTempId"], unique = true)]
)
data class MessageEntity(
    @PrimaryKey val id: String, // Server-generated UUID (or clientTempId initially)
    val chatId: String, // UUID of the chat this message belongs to
    val senderId: String, // UUID of the sender
    val receiverId: String?, // UUID of the receiver (useful for 1:1 chats if not directly in ChatEntity)
    val content: String,
    val timestamp: Long, // Epoch milliseconds, for sorting and display
    var status: String, // "sending", "sent", "delivered", "read", "failed"
    val clientTempId: String? = null, // Client-generated temporary ID for optimistic updates and ACK matching
    val isOptimistic: Boolean = false // Flag to indicate if this is an optimistic update not yet confirmed by server
) {
    companion object {
        const val STATUS_SENDING = "sending"
        const val STATUS_SENT = "sent"
        const val STATUS_DELIVERED = "delivered"
        const val STATUS_READ = "read"
        const val STATUS_FAILED = "failed"
    }
}