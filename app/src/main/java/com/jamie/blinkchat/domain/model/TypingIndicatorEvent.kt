package com.jamie.blinkchat.domain.model

data class TypingIndicatorEvent(
    val chatId: String,
    val userId: String, // The user who is typing
    val isTyping: Boolean
    // val username: String? = null // Optional: if you want to include username directly
)