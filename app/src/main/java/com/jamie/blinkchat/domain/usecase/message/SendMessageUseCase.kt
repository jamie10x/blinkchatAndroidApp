package com.jamie.blinkchat.domain.usecase.message

import com.jamie.blinkchat.repositories.MessageRepository

import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.domain.model.Message
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    operator fun invoke(
        content: String,
        chatId: String?, // Nullable if it's a new chat being started with a receiverId
        receiverId: String? // Nullable if message is for an existing chatId
    ): Flow<Resource<out Message>> {
        // Validate input (basic - more can be added)
        if (content.isBlank()) {
            // Consider returning Flow<Resource.Error> directly or let repository handle
            // For now, assuming repository might also have some validation or specific error
        }
        if (chatId.isNullOrBlank() && receiverId.isNullOrBlank()) {
            // throw IllegalArgumentException("Either chatId or receiverId must be provided.")
            // Or return a Flow emitting an error
        }

        val clientTempId = UUID.randomUUID().toString()
        return messageRepository.sendMessage(
            content = content.trim(),
            chatId = chatId,
            receiverId = receiverId,
            clientTempId = clientTempId
        )
    }
}