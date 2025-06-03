package com.jamie.blinkchat.domain.usecase.chat_list

import javax.inject.Inject

data class ChatUseCases @Inject constructor(
    val getChatList: GetChatListUseCase
    // Add other chat-related use cases here later:
    // val getMessages: GetMessagesUseCase,
    // val sendMessage: SendMessageUseCase
)