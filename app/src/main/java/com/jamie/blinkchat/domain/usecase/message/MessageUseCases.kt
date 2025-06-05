package com.jamie.blinkchat.domain.usecase.message

import javax.inject.Inject

data class MessageUseCases @Inject constructor(
    val getChatMessages: GetChatMessagesUseCase,
    val sendMessage: SendMessageUseCase,
    val loadOlderMessages: LoadOlderMessagesUseCase,
    val updateMessageStatus: UpdateMessageStatusUseCase,
    val sendTypingIndicator: SendTypingIndicatorUseCase,
    val observeTypingIndicator: ObserveTypingIndicatorUseCase
)
