package com.jamie.blinkchat.data.model.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    val id: String, // UUID
    @SerialName("chatId") val chatId: String, // UUID
    @SerialName("senderId") val senderId: String, // UUID
    // receiverId is not explicitly in the MessageDto from GET /messages or POST /messages response in your docs for the message itself
    val content: String,
    val timestamp: String, // ISO 8601 timestamp
    val status: String, // "sent" | "delivered" | "read"
    val sender: PublicUserDto? = null // Sender can be populated
)

@Serializable
data class ChatDto(
    val id: String, // UUID
    @SerialName("createdAt") val createdAt: String, // ISO 8601 timestamp
    val otherParticipants: List<PublicUserDto>,
    val lastMessage: MessageDto? = null, // Nullable if chat has no messages (updated to use MessageDto)
    val unreadCount: Int
)

// New DTO for POST /api/v1/messages request
@Serializable
data class CreateMessageRequestDto(
    @SerialName("chatId") val chatId: String? = null,
    @SerialName("receiverId") val receiverId: String? = null,
    val content: String
)