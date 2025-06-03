package com.jamie.blinkchat.domain.model

data class ChatListItem(
    val chatId: String,
    val otherParticipantId: String, // Assuming 1:1 chats primarily for now
    val otherParticipantUsername: String,
    // val otherParticipantAvatarUrl: String? = null, // If you plan to have avatars
    val lastMessageContent: String?, // Nullable if no messages yet
    val lastMessageTimestamp: String?, // ISO 8601 String, nullable
    val unreadCount: Int,
    val lastMessageSenderIsSelf: Boolean? // Nullable if no last message
)