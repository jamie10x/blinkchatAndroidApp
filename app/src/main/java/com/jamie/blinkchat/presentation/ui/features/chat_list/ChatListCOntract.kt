package com.jamie.blinkchat.presentation.ui.features.chat_list

import com.jamie.blinkchat.core.mvi.UiEffect
import com.jamie.blinkchat.core.mvi.UiIntent
import com.jamie.blinkchat.core.mvi.UiState
import com.jamie.blinkchat.domain.model.ChatSummaryItem

object ChatListContract {

    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val chats: List<ChatSummaryItem> = emptyList(),
        val errorMessage: String? = null,
        val isUserLoggedIn: Boolean = true
    ) : UiState

    sealed interface Intent : UiIntent {
        object LoadChatList : Intent
        object RefreshChatList : Intent
        data class ChatClicked(val chatId: String) : Intent // Only chatId needed here
        object LogoutClicked : Intent
        object ErrorMessageShown : Intent
    }

    sealed interface Effect : UiEffect {
        // Modified to include otherUsername
        data class NavigateToChat(val chatId: String, val otherUsername: String) : Effect
        object NavigateToLogin : Effect
        data class ShowErrorSnackbar(val message: String) : Effect
    }
}