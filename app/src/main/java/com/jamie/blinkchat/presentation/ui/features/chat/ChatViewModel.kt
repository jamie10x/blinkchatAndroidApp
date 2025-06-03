package com.jamie.blinkchat.presentation.ui.features.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.core.mvi.BaseViewModel
import com.jamie.blinkchat.data.model.remote.websockets.WebSocketConnectionState
import com.jamie.blinkchat.data.model.remote.websockets.WebSocketManager
import com.jamie.blinkchat.domain.model.Message
import com.jamie.blinkchat.domain.usecase.auth.AuthUseCases
import com.jamie.blinkchat.domain.usecase.message.MessageUseCases
import com.jamie.blinkchat.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageUseCases: MessageUseCases,
    private val authUseCases: AuthUseCases,
    webSocketManager: WebSocketManager,
    savedStateHandle: SavedStateHandle
) : BaseViewModel<ChatContract.State, ChatContract.Intent, ChatContract.Effect>() {

    private var messagesJob: Job? = null
    private var olderMessagesJob: Job? = null
    private var typingIndicatorJob: Job? = null
    private var currentChatId: String? = null

    init {
        // Get current user ID for message display logic
        viewModelScope.launch {
            val userResource = authUseCases.getCurrentUser().first() // Get user once
            if (userResource is Resource.Success) {
                setState { copy(currentUserId = userResource.data?.id) }
            } else {
                // Handle case where user ID can't be fetched (should ideally not happen if user is here)
                Timber.e("Could not fetch current user ID in ChatViewModel")
                setEffect { ChatContract.Effect.ShowErrorSnackbar("Error: User session invalid.") }
                // Consider navigating back or to login if this is critical
            }
        }

        // Observe WebSocket connection state
        webSocketManager.connectionState
            .onEach { state ->
                setState { copy(isConnected = state is WebSocketConnectionState.Connected) }
                if (state is WebSocketConnectionState.Connected && currentChatId != null) {
                    // If reconnected, maybe resend pending or refresh messages
                    Timber.d("ChatViewModel: WebSocket reconnected for chatId: $currentChatId")
                }
            }
            .launchIn(viewModelScope)

        // Extract chatId from SavedStateHandle (passed via navigation)
        savedStateHandle.get<String>(Screen.Chat.ARG_CHAT_ID)?.let { id ->
            setIntent(ChatContract.Intent.LoadChatDetails(id))
        } ?: run {
            Timber.e("ChatViewModel: ChatId not found in SavedStateHandle.")
            setState { copy(isLoadingMessages = false, loadMessagesError = "Chat information missing.") }
            // Consider navigating back or showing a more permanent error
        }
    }

    override fun createInitialState(): ChatContract.State {
        return ChatContract.State()
    }

    override fun handleIntent(intent: ChatContract.Intent) {
        when (intent) {
            is ChatContract.Intent.LoadChatDetails -> {
                currentChatId = intent.chatId
                setState { copy(chatId = intent.chatId, isLoadingMessages = true, messages = emptyList(), loadMessagesError = null) }
                // TODO: Fetch other participant username based on chatId (needs new use case/repo method)
                // setState { copy(otherParticipantUsername = "Opponent") } // Placeholder
                observeMessages(intent.chatId)
                // Mark messages as delivered when chat is opened (if applicable)
                markMessagesAs(intent.chatId, Message.STATUS_DELIVERED) // Example
            }
            is ChatContract.Intent.LoadOlderMessages -> {
                currentChatId?.let { loadOlderMessages(it) }
            }
            is ChatContract.Intent.InputTextChanged -> {
                setState { copy(currentInputText = intent.text) }
                // Send typing indicator if text is not empty and chatId is known
                currentChatId?.let { sendTypingIndicator(it, intent.text.isNotBlank()) }
            }
            is ChatContract.Intent.SendMessageClicked -> {
                currentChatId?.let { sendMessage(it, uiState.value.currentInputText) }
                    ?: run { // Case where chatId is somehow null, but we have receiverId (e.g. new chat)
                        // This logic needs to be more robust if we allow starting new chats from a "user profile" screen.
                        // For now, ChatScreen assumes an existing chatId.
                        Timber.w("SendMessageClicked: ChatId is null. This flow is not fully handled yet.")
                        // val receiverId = uiState.value.otherParticipantId // Would need this from state
                        // sendMessage(null, uiState.value.currentInputText, receiverId = receiverId)
                    }
            }
            is ChatContract.Intent.ClearSendMessageError -> {
                setState { copy(sendMessageError = null) }
            }
            is ChatContract.Intent.ClearLoadMessagesError -> {
                setState { copy(loadMessagesError = null) }
            }
            is ChatContract.Intent.TypingIndicatorChanged -> { // Explicit call from UI
                currentChatId?.let { sendTypingIndicator(it, intent.isTyping) }
            }
            is ChatContract.Intent.MessagesDisplayed -> {
                currentChatId?.let { markMessagesAsRead(it, intent.messageIds) }
            }
            is ChatContract.Intent.RetryLoadMessages -> {
                currentChatId?.let {
                    setState { copy(isLoadingMessages = true, loadMessagesError = null) }
                    observeMessages(it)
                }
            }
        }
    }

    private fun observeMessages(chatId: String) {
        messagesJob?.cancel() // Cancel previous observer if chatId changes
        messagesJob = messageUseCases.getChatMessages(chatId)
            .onEach { messages ->
                setState {
                    copy(
                        isLoadingMessages = false, // Initial load done once first batch arrives
                        messages = messages,
                        // Determine if more can be loaded based on if the oldest message has changed
                        // This is a simplification; more robust pagination would check if less than limit was fetched
                        canLoadMoreOlderMessages = messages.isNotEmpty() // Basic check
                    )
                }
                // After new messages are loaded and displayed, mark them as read
                // This needs to be coordinated with UI telling us which messages are visible.
                // For now, let's assume all incoming messages for this chat should be marked read IF app is foreground & chat is open
                // But MessagesDisplayed intent is better.
            }
            .launchIn(viewModelScope)
    }

    private fun loadOlderMessages(chatId: String) {
        if (uiState.value.isLoadingOlderMessages || !uiState.value.canLoadMoreOlderMessages) return
        olderMessagesJob?.cancel()

        setState { copy(isLoadingOlderMessages = true, loadMessagesError = null) }

        val currentOldestTimestamp = uiState.value.messages.firstOrNull()?.timestamp ?: System.currentTimeMillis()
        // The 'beforeTimestamp' our repository treats as an offset currently.
        // Proper pagination would use the actual timestamp or a page number/cursor.
        // For this example, if we treat it as offset, then subsequent loads would increase this.
        // Let's use a simple page/offset for now based on current message count for demo purposes.
        val offset = uiState.value.messages.size // Simple offset for next page

        olderMessagesJob = viewModelScope.launch {
            when (val result = messageUseCases.loadOlderMessages(chatId, offset.toLong() /* using count as offset */, 20)) {
                is Resource.Success -> {
                    setState {
                        copy(
                            isLoadingOlderMessages = false,
                            // Messages are prepended by repository observing Room, so uiState.value.messages will update
                            canLoadMoreOlderMessages = (result.data
                                ?: 0) >= 20 // If less than limit fetched, no more
                        )
                    }
                    if (result.data == 0) {
                        setState { copy(canLoadMoreOlderMessages = false) }
                    }
                }
                is Resource.Error -> {
                    setState {
                        copy(
                            isLoadingOlderMessages = false,
                            loadMessagesError = result.message ?: "Failed to load older messages."
                        )
                    }
                }
                is Resource.Loading -> { /* Already handled by isLoadingOlderMessages = true */ }
            }
        }
    }

    private fun sendMessage(chatId: String, content: String, receiverId: String? = null) {
        if (content.isBlank()) return

        val trimmedContent = content.trim()
        setState { copy(currentInputText = "") } // Clear input field immediately

        messageUseCases.sendMessage(trimmedContent, chatId, receiverId)
            .onEach { resource ->
                when (resource) {
                    is Resource.Loading -> { /* Optimistic message already handled by UI via initial emission */ }
                    is Resource.Success -> {
                        // Message is now confirmed or updated in local DB by repository
                        // The Flow from getChatMessages will automatically update the UI.
                        Timber.d("SendMessage: Success for message ${resource.data?.id}")
                        setEffect { ChatContract.Effect.MessageSentSuccessfully } // e.g., to scroll
                        setEffect { ChatContract.Effect.ScrollToBottom }
                    }
                    is Resource.Error -> {
                        Timber.e("SendMessage: Error - ${resource.message}")
                        setState { copy(sendMessageError = resource.message ?: "Failed to send message") }
                        // Optionally, revert optimistic message or show retry
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private var lastTypingStateSent = false
    private fun sendTypingIndicator(chatId: String, isTyping: Boolean) {
        // Debounce or send only on change
        if (isTyping == lastTypingStateSent) return
        lastTypingStateSent = isTyping

        typingIndicatorJob?.cancel() // Cancel previous delayed job if any
        typingIndicatorJob = viewModelScope.launch {
            if (isTyping) { // Send immediately if typing starts
                messageUseCases.sendTypingIndicator(chatId, true)
            } else { // Delay sending "stopped typing"
                delay(1500) // Send "stopped typing" after 1.5s of no input
                messageUseCases.sendTypingIndicator(chatId, false)
            }
        }
    }

    private fun markMessagesAsRead(chatId: String, messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            // Filter out messages already marked as read or sent by current user.
            // This requires message objects, so this logic might be better in use case or repo.
            // For now, assuming UI sends IDs of unread messages from others.
            messageUseCases.updateMessageStatus(chatId, messageIds, Message.STATUS_READ)
            Timber.d("Attempted to mark messages as read: $messageIds")
        }
    }

    private fun markMessagesAs(chatId: String, status: String) {
        // This is a general example; specific logic for "delivered" would be automated by server/client ACKs
        // For "read", it's driven by UI interaction (MessagesDisplayed intent)
        viewModelScope.launch {
            // messageUseCases.updateMessageStatus(chatId, listOf("someMessageIdToMarkDelivered"), status)
        }
    }

    override fun onCleared() {
        super.onCleared()
        messagesJob?.cancel()
        olderMessagesJob?.cancel()
        typingIndicatorJob?.cancel()
        // If user is in a chat screen and closes app, send "stopped typing"
        currentChatId?.let {
            if (lastTypingStateSent) {
                viewModelScope.launch { messageUseCases.sendTypingIndicator(it, false) }
            }
        }
    }
}