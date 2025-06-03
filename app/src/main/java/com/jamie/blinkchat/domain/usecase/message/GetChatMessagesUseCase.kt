package com.jamie.blinkchat.domain.usecase.message

import com.jamie.blinkchat.domain.model.Message
import com.jamie.blinkchat.repositories.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetChatMessagesUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    operator fun invoke(chatId: String): Flow<List<Message>> {
        return messageRepository.getMessagesForChat(chatId)
    }
}