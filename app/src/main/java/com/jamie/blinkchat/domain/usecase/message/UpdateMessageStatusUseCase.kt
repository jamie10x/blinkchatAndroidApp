package com.jamie.blinkchat.domain.usecase.message

import com.jamie.blinkchat.repositories.MessageRepository
import javax.inject.Inject

class UpdateMessageStatusUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(chatId: String, messageIds: List<String>, status: String) {
        if (messageIds.isNotEmpty()) {
            messageRepository.updateMessageStatus(chatId, messageIds, status)
        }
    }
}