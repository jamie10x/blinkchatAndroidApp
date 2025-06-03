package com.jamie.blinkchat.presentation.ui.features.chat_list

import com.jamie.blinkchat.core.mvi.UiEffect
import com.jamie.blinkchat.core.mvi.UiIntent
import com.jamie.blinkchat.core.mvi.UiState
import com.jamie.blinkchat.domain.model.ChatSummaryItem

object ChatListContract {

    /**
     * Represents the state of the ChatList screen.
     *
     * @param isLoading True if the initial chat list is being loaded.
     * @param isRefreshing True if a manual refresh (e.g., swipe-to-refresh) is in progress.
     * @param chats The list of chat summary items to display.
     * @param errorMessage An error message to display if loading or refreshing fails.
     * @param isUserLoggedIn Used to determine if we should even attempt to load chats. Could also manage logout.
     */
    data class State(
        val isLoading: Boolean = true, // For initial load
        val isRefreshing: Boolean = false, // For swipe-to-refresh or manual refresh
        val chats: List<ChatSummaryItem> = emptyList(),
        val errorMessage: String? = null,
        val isUserLoggedIn: Boolean = true // Assume logged in initially, Splash handles actual check
    ) : UiState

    /**
     * Defines the intents (user actions or events) for the ChatList screen.
     */
    sealed interface Intent : UiIntent {
        object LoadChatList : Intent // Triggered on screen init or retry
        object RefreshChatList : Intent // Triggered by swipe-to-refresh or a refresh button
        data class ChatClicked(val chatId: String) : Intent
        object LogoutClicked : Intent
        object ErrorMessageShown : Intent // To clear error message from state after UI has shown it
    }

    /**
     * Defines the one-time effects (side effects) for the ChatList screen.
     */
    sealed interface Effect : UiEffect {
        data class NavigateToChat(val chatId: String) : Effect
        object NavigateToLogin : Effect // After logout
        data class ShowErrorSnackbar(val message: String) : Effect
        // data class ShowLogoutConfirmationDialog : Effect // Optional for logout confirmation
    }
}