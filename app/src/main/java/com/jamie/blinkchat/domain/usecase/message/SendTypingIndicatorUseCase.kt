package com.jamie.blinkchat.domain.usecase.message

import com.jamie.blinkchat.repositories.MessageRepository
import javax.inject.Inject

class SendTypingIndicatorUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(chatId: String, isTyping: Boolean) {
        messageRepository.sendTypingIndicator(chatId, isTyping)
    }
}