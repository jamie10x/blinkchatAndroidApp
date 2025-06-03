package com.jamie.blinkchat.data.model.remote.websockets

import com.jamie.blinkchat.data.model.remote.MessageDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement // For flexible payload

/**
 * Generic wrapper for all WebSocket messages.
 * The 'payload' is type-erased here and will be deserialized to a specific type
 * based on the 'type' field.
 */
@Serializable
data class WebSocketWrapperDto(
    val type: String,
    val payload: JsonElement // Using JsonElement for flexibility before specific parsing
)

// --- Client-to-Server Payloads ---

object ClientWebSocketSendType {
    const val NEW_MESSAGE = "new_message"
    const val MESSAGE_STATUS_UPDATE = "message_status_update"
    const val TYPING_INDICATOR = "typing_indicator"
}

@Serializable
data class ClientNewMessagePayloadDto(
    @SerialName("chatId") val chatId: String? = null, // UUID
    @SerialName("receiverId") val receiverId: String? = null, // UUID
    val content: String,
    @SerialName("clientTempId") val clientTempId: String? = null // Client-generated temp ID
)

@Serializable
data class ClientMessageStatusUpdatePayloadDto(
    @SerialName("messageId") val messageId: String, // UUID
    @SerialName("chatId") val chatId: String, // UUID
    val status: String // "delivered" or "read"
)

@Serializable
data class ClientTypingIndicatorPayloadDto(
    @SerialName("chatId") val chatId: String, // UUID
    @SerialName("userId") val userId: String, // UUID of the typing user
    @SerialName("isTyping") val isTyping: Boolean
)


// --- Server-to-Client Payloads & Types ---

object ServerWebSocketReceiveType {
    const val NEW_MESSAGE = "new_message" // Server sends a full Message object
    const val MESSAGE_SENT_ACK = "message_sent_ack"
    const val MESSAGE_STATUS_UPDATE = "message_status_update"
    const val TYPING_INDICATOR = "typing_indicator"
    const val ERROR = "error"
}

// For "new_message" from server, your docs say: "Payload is models.Message with Sender populated"
// We'll use SimpleMessageDto here as it's close to the structure provided.
// If `models.Message` is more complex, create a distinct DTO for it.
typealias ServerNewMessagePayloadDto = MessageDto

@Serializable
data class ServerMessageSentAckPayloadDto(
    @SerialName("clientTempId") val clientTempId: String?, // Client's temporary ID
    @SerialName("messageId") val messageId: String, // Actual ID of the saved message (UUID)
    @SerialName("chatId") val chatId: String, // UUID
    val timestamp: String // ISO 8601 timestamp of when it was saved/sent
    // Potentially include initial status like "sent" if needed
)

@Serializable
data class ServerMessageStatusUpdatePayloadDto(
    @SerialName("messageId") val messageId: String, // UUID
    @SerialName("chatId") val chatId: String, // UUID
    val status: String, // "sent", "delivered", or "read"
    @SerialName("userId") val userId: String, // UUID of user whose action triggered this (e.g., who read it)
    val timestamp: String // ISO 8601 timestamp of the status update
)

@Serializable
data class ServerTypingIndicatorPayloadDto(
    @SerialName("chatId") val chatId: String, // UUID
    @SerialName("userId") val userId: String, // UUID of the typing user
    @SerialName("isTyping") val isTyping: Boolean
    // Consider adding username if the client needs to display it directly from this payload
    // @SerialName("username") val username: String? = null
)

@Serializable
data class ServerErrorPayloadDto(
    val message: String,
    @SerialName("originalMessageType") val originalMessageType: String? = null, // Optional: type of client message that caused error
    val code: Int? = null // Optional error code
)