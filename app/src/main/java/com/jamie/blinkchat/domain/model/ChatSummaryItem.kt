package com.jamie.blinkchat.domain.model

data class ChatSummaryItem(
    val chatId: String,
    val otherParticipantUsername: String,
    // val otherParticipantAvatarUrl: String?, // Future
    val lastMessagePreview: String?,
    val lastMessageTimestamp: Long?, // Epoch milliseconds
    val lastMessageStatus: String?, // "sent", "delivered", "read"
    val isLastMessageFromCurrentUser: Boolean,
    val unreadCount: Int
)