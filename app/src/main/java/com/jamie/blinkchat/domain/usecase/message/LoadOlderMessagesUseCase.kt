package com.jamie.blinkchat.domain.usecase.message

import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.repositories.MessageRepository
import javax.inject.Inject

class LoadOlderMessagesUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(chatId: String, beforeTimestamp: Long, limit: Int = 20): Resource<Int> {
        // 'beforeTimestamp' here would be the timestamp of the current oldest message in the UI
        // or an offset if the repository/API handles it that way.
        // Our repository currently interprets 'beforeTimestamp' as an offset for simplicity.
        return messageRepository.loadOlderMessages(chatId, beforeTimestamp, limit)
    }
}