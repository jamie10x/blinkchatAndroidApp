package com.jamie.blinkchat.domain.usecase.message

import com.jamie.blinkchat.domain.model.TypingIndicatorEvent
import com.jamie.blinkchat.repositories.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTypingIndicatorUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    /**
     * Observes typing indicator events for a specific chat.
     *
     * @param chatId The ID of the chat to observe.
     * @return A Flow emitting [TypingIndicatorEvent] objects for other users in the chat.
     */
    operator fun invoke(chatId: String): Flow<TypingIndicatorEvent> {
        return messageRepository.observeTypingIndicators(chatId)
    }
}