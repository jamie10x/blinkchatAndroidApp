package com.jamie.blinkchat.domain.model

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderUsername: String?, // Denormalized for easier display
    val content: String,
    val timestamp: Long, // Epoch milliseconds
    val status: String,
    val isFromCurrentUser: Boolean,
    val clientTempId: String? = null,
    val isOptimistic: Boolean = false
) {
    // Companion object with status constants, making them accessible via Message.STATUS_SENT
    companion object {
        const val STATUS_SENDING = "sending"
        const val STATUS_SENT = "sent"
        const val STATUS_DELIVERED = "delivered"
        const val STATUS_READ = "read"
        const val STATUS_FAILED = "failed"
    }
}