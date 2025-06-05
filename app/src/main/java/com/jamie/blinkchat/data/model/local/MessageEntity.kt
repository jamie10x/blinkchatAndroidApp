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
    val chatId: String,
    val senderId: String,
    val receiverId: String?,
    val content: String,
    val timestamp: Long,
    var status: String, // "sending", "sent", "delivered", "read", "failed"
    val clientTempId: String? = null, // Key for identifying unsent messages
    val isOptimistic: Boolean = false // Another key indicator
) {
    companion object {
        const val STATUS_SENDING = "sending"
        const val STATUS_SENT = "sent"
        const val STATUS_DELIVERED = "delivered"
        const val STATUS_READ = "read"
        const val STATUS_FAILED = "failed"
    }
}