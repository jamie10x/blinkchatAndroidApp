package com.jamie.blinkchat.presentation.ui.features.chat

import com.jamie.blinkchat.core.mvi.UiEffect
import com.jamie.blinkchat.core.mvi.UiIntent
import com.jamie.blinkchat.core.mvi.UiState
import com.jamie.blinkchat.domain.model.Message

object ChatContract {

    /**
     * Represents the state of the Chat screen for a specific chat.
     *
     * @param chatId The ID of the current chat.
     * @param otherParticipantUsername The username of the other participant in the chat.
     * @param messages The list of messages to display in this chat.
     * @param currentInputText The text currently typed by the user in the message input field.
     * @param isLoadingMessages True if messages are initially being loaded.
     * @param isLoadingOlderMessages True if older messages are being loaded for pagination.
     * @param canLoadMoreOlderMessages True if there are more older messages that can be fetched.
     * @param sendMessageError An error message related to sending a message, null otherwise.
     * @param loadMessagesError An error message related to loading messages, null otherwise.
     * @param isOtherUserTyping True if the other user is currently typing.
     * @param currentUserId The ID of the logged-in user (needed to determine message alignment etc.).
     * @param isConnected True if the WebSocket connection is active.
     */
    data class State(
        val chatId: String? = null, // Will be set from navigation argument
        val otherParticipantUsername: String? = null, // TODO: Fetch or pass this
        val messages: List<Message> = emptyList(),
        val currentInputText: String = "",
        val isLoadingMessages: Boolean = true,
        val isLoadingOlderMessages: Boolean = false,
        val canLoadMoreOlderMessages: Boolean = true, // Assume true initially
        val sendMessageError: String? = null,
        val loadMessagesError: String? = null,
        val isOtherUserTyping: Boolean = false, // For typing indicator
        val currentUserId: String? = null, // To determine sent vs received messages
        val isConnected: Boolean = false // WebSocket connection status
    ) : UiState

    /**
     * Defines the intents (user actions or events) for the Chat screen.
     */
    sealed interface Intent : UiIntent {
        data class LoadChatDetails(val chatId: String) : Intent // Initial load for a given chat
        object LoadOlderMessages : Intent
        data class InputTextChanged(val text: String) : Intent
        object SendMessageClicked : Intent
        object ClearSendMessageError : Intent
        object ClearLoadMessagesError : Intent
        data class TypingIndicatorChanged(val isTyping: Boolean) : Intent
        // When messages are displayed and should be marked as "read"
        data class MessagesDisplayed(val messageIds: List<String>) : Intent
        object RetryLoadMessages : Intent
    }

    /**
     * Defines the one-time effects (side effects) for the Chat screen.
     */
    sealed interface Effect : UiEffect {
        object MessageSentSuccessfully : Effect // e.g., to clear input field
        data class ShowErrorSnackbar(val message: String) : Effect
        object ScrollToBottom : Effect // After sending a message or receiving a new one
        // object NavigateBack : Effect // If needed for specific back navigation logic
    }
}