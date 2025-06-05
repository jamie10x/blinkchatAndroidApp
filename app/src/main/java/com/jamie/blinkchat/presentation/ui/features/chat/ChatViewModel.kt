package com.jamie.blinkchat.presentation.ui.features.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.core.mvi.BaseViewModel
import com.jamie.blinkchat.data.model.remote.websockets.WebSocketConnectionState
import com.jamie.blinkchat.data.model.remote.websockets.WebSocketManager
import com.jamie.blinkchat.domain.model.Message
import com.jamie.blinkchat.domain.model.TypingIndicatorEvent
import com.jamie.blinkchat.domain.usecase.auth.AuthUseCases
import com.jamie.blinkchat.domain.usecase.message.MessageUseCases
import com.jamie.blinkchat.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
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
    private var typingIndicatorObservationJob: Job? = null
    private var selfTypingIndicatorJob: Job? = null
    private var clearOtherUserTypingJob: Job? = null

    private var receiverIdForNewChat: String? = null

    companion object {
        private const val TYPING_INDICATOR_TIMEOUT_MS = 3000L
    }

    init {
        viewModelScope.launch {
            val userResource = authUseCases.getCurrentUser().firstOrNull { it !is Resource.Loading } // Wait for non-loading
            if (userResource is Resource.Success && userResource.data != null) {
                setState { copy(currentUserId = userResource.data.id) }
            } else {
                Timber.e("Could not fetch current user ID in ChatViewModel: ${userResource?.message}")
                setEffect { ChatContract.Effect.ShowErrorSnackbar("Error: User session invalid.") }
            }
        }

        webSocketManager.connectionState
            .onEach { connectionState ->
                val isConnected = connectionState is WebSocketConnectionState.Connected
                setState { copy(isConnected = isConnected) }
                if (isConnected && uiState.value.chatId != null && uiState.value.chatId != Screen.Chat.NEW_CHAT_PLACEHOLDER_ID) {
                    Timber.d("ChatViewModel: WebSocket reconnected for chatId: ${uiState.value.chatId}")
                }
            }
            .launchIn(viewModelScope)

        val chatIdFromNav = savedStateHandle.get<String>(Screen.Chat.ARG_CHAT_ID)
        val encodedUsernameFromNav = savedStateHandle.get<String>(Screen.Chat.ARG_OTHER_USERNAME)
        val receiverIdFromNav = savedStateHandle.get<String>(Screen.Chat.ARG_RECEIVER_ID) // Get receiverId

        val otherUsername = encodedUsernameFromNav?.let { encoded ->
            try { URLDecoder.decode(encoded, StandardCharsets.UTF_8.toString()) }
            catch (e: Exception) { Timber.e(e, "Failed to decode username: $encoded"); "Chat Partner" }
        } ?: "Chat Partner"

        setState { copy(otherParticipantUsername = otherUsername) }

        if (chatIdFromNav != null) {
            if (chatIdFromNav == Screen.Chat.NEW_CHAT_PLACEHOLDER_ID && receiverIdFromNav != null) {
                // This is a new chat to be initiated
                this.receiverIdForNewChat = receiverIdFromNav
                Timber.i("ChatViewModel: Initializing for new chat with receiverId: $receiverIdFromNav, username: $otherUsername")
                setState {
                    copy(
                        chatId = null, // Explicitly null for a new chat until first message is sent
                        isLoadingMessages = false, // No messages to load initially
                        canLoadMoreOlderMessages = false // No history for a new chat
                    )
                }
            } else if (chatIdFromNav != Screen.Chat.NEW_CHAT_PLACEHOLDER_ID) {
                // Existing chat
                Timber.i("ChatViewModel: Initializing for existing chatId: $chatIdFromNav")
                setIntent(ChatContract.Intent.LoadChatDetails(chatIdFromNav))
            } else {
                // Invalid state (e.g. "new" chatId but no receiverId)
                Timber.e("ChatViewModel: Invalid navigation arguments for chat. ChatId: $chatIdFromNav, ReceiverId: $receiverIdFromNav")
                setState { copy(isLoadingMessages = false, loadMessagesError = "Chat information invalid.") }
            }
        } else {
            Timber.e("ChatViewModel: ChatId not found in SavedStateHandle.")
            setState { copy(isLoadingMessages = false, loadMessagesError = "Chat information missing.") }
        }
    }

    override fun createInitialState(): ChatContract.State {
        return ChatContract.State()
    }

    override fun handleIntent(intent: ChatContract.Intent) {
        val currentChatIdFromState = uiState.value.chatId
        // val currentReceiverIdForNewChat = receiverIdForNewChat // Use this if needed

        when (intent) {
            is ChatContract.Intent.LoadChatDetails -> {
                // This is for existing chats
                if (intent.chatId != Screen.Chat.NEW_CHAT_PLACEHOLDER_ID) {
                    setState { copy(chatId = intent.chatId, isLoadingMessages = true, messages = emptyList(), loadMessagesError = null) }
                    observeMessages(intent.chatId)
                    observeOtherUserTyping(intent.chatId)
                }
            }
            is ChatContract.Intent.LoadOlderMessages -> {
                currentChatIdFromState?.let { loadOlderMessages(it) }
            }
            is ChatContract.Intent.InputTextChanged -> {
                setState { copy(currentInputText = intent.text) }
                val chatIdForTyping = currentChatIdFromState ?: receiverIdForNewChat?.let { "new_chat_with_$it" } // Temporary concept for typing before chatID exists
                chatIdForTyping?.let { sendTypingIndicator(it, intent.text.isNotBlank()) } // Typing indicator might need actual chatId or different handling for new chats
            }
            is ChatContract.Intent.SendMessageClicked -> {
                sendCurrentMessage()
            }
            // ... (Clear errors, TypingIndicatorChanged, MessagesDisplayed, RetryLoadMessages as before) ...
            is ChatContract.Intent.ClearSendMessageError -> setState { copy(sendMessageError = null) }
            is ChatContract.Intent.ClearLoadMessagesError -> setState { copy(loadMessagesError = null) }
            is ChatContract.Intent.TypingIndicatorChanged -> {
                val chatIdForTyping = currentChatIdFromState ?: receiverIdForNewChat?.let { "new_chat_with_$it" }
                chatIdForTyping?.let { sendTypingIndicator(it, intent.isTyping) }
            }
            is ChatContract.Intent.MessagesDisplayed -> {
                currentChatIdFromState?.let { markMessagesAsRead(it, intent.messageIds) }
            }
            is ChatContract.Intent.RetryLoadMessages -> {
                currentChatIdFromState?.let {
                    setState { copy(isLoadingMessages = true, loadMessagesError = null) }
                    observeMessages(it)
                    observeOtherUserTyping(it)
                }
            }
        }
    }

    private fun observeMessages(chatId: String) {
        messagesJob?.cancel()
        messagesJob = messageUseCases.getChatMessages(chatId)
            .onEach { messages ->
                setState {
                    copy(
                        isLoadingMessages = false,
                        messages = messages,
                        canLoadMoreOlderMessages = messages.isNotEmpty() && messages.size >= 20 // Example: if less than full page loaded, maybe no more
                    )
                }
                // After first batch of messages for an existing chat, update state.chatId if it was provisional
                if (uiState.value.chatId == null || uiState.value.chatId == Screen.Chat.NEW_CHAT_PLACEHOLDER_ID) {
                    messages.firstOrNull()?.chatId?.let { actualChatId ->
                        if (actualChatId != Screen.Chat.NEW_CHAT_PLACEHOLDER_ID) {
                            Timber.i("ChatViewModel: Actual chatId $actualChatId established from first message.")
                            setState { copy(chatId = actualChatId) }
                            // Re-observe typing with actual chatId if it changed from a placeholder
                            if (this.receiverIdForNewChat != null) { // Means it was a new chat
                                observeOtherUserTyping(actualChatId)
                                this.receiverIdForNewChat = null // Clear it as chat is now established
                            }
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeOtherUserTyping(chatId: String) {
        // ... (observeOtherUserTyping implementation as before) ...
        typingIndicatorObservationJob?.cancel()
        typingIndicatorObservationJob = messageUseCases.observeTypingIndicator(chatId)
            .onEach { event: TypingIndicatorEvent ->
                if (event.userId != uiState.value.currentUserId) {
                    if (event.isTyping) {
                        setState { copy(isOtherUserTyping = true) }
                        clearOtherUserTypingJob?.cancel()
                        clearOtherUserTypingJob = viewModelScope.launch {
                            delay(TYPING_INDICATOR_TIMEOUT_MS)
                            if (uiState.value.isOtherUserTyping) {
                                Timber.d("Typing indicator timeout for user ${event.userId}, setting to false.")
                                setState { copy(isOtherUserTyping = false) }
                            }
                        }
                    } else {
                        clearOtherUserTypingJob?.cancel()
                        setState { copy(isOtherUserTyping = false) }
                    }
                }
            }
            .launchIn(viewModelScope)
    }


    private fun loadOlderMessages(chatId: String) {
        // ... (loadOlderMessages implementation as before) ...
        if (uiState.value.isLoadingOlderMessages || !uiState.value.canLoadMoreOlderMessages) return
        olderMessagesJob?.cancel()
        setState { copy(isLoadingOlderMessages = true, loadMessagesError = null) }
        val offset = uiState.value.messages.size
        olderMessagesJob = viewModelScope.launch {
            when (val result = messageUseCases.loadOlderMessages(chatId, offset.toLong(), 20)) {
                is Resource.Success -> {
                    val fetchedCount = result.data ?: 0
                    setState { copy(isLoadingOlderMessages = false, canLoadMoreOlderMessages = fetchedCount >= 20) }
                }
                is Resource.Error -> {
                    setState { copy(isLoadingOlderMessages = false, loadMessagesError = result.message ?: "Failed to load older messages.") }
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun sendCurrentMessage() {
        val content = uiState.value.currentInputText.trim()
        if (content.isBlank()) return

        val currentChatIdState = uiState.value.chatId
        val currentReceiverIdForNewChat = receiverIdForNewChat // Use the stored receiverId for new chats

        // Stop self-typing indicator
        val chatIdForTyping = currentChatIdState ?: currentReceiverIdForNewChat?.let { "new_chat_with_$it" }
        chatIdForTyping?.let { sendTypingIndicator(it, false) }

        setState { copy(currentInputText = "") } // Clear input field immediately

        messageUseCases.sendMessage(
            content = content,
            chatId = currentChatIdState, // This will be null if it's a new chat
            receiverId = if (currentChatIdState == null) currentReceiverIdForNewChat else null
        )
            .onEach { resource ->
                when (resource) {
                    is Resource.Loading -> { /* Optimistic UI update handled by message list observation */ }
                    is Resource.Success -> {
                        Timber.d("SendMessage: Success for message ${resource.data?.id}. ChatId from resource: ${resource.data?.chatId}")
                        // If this was the first message of a new chat, the resource.data.chatId is the new actual chatId
                        resource.data?.chatId?.let { actualChatId ->
                            if (uiState.value.chatId == null || uiState.value.chatId == Screen.Chat.NEW_CHAT_PLACEHOLDER_ID) {
                                if (actualChatId != Screen.Chat.NEW_CHAT_PLACEHOLDER_ID) {
                                    Timber.i("ChatViewModel: New chat established. Actual chatId: $actualChatId")
                                    setState { copy(chatId = actualChatId) }
                                    // Re-observe messages and typing indicators with the actual chatId
                                    observeMessages(actualChatId)
                                    observeOtherUserTyping(actualChatId)
                                    this.receiverIdForNewChat = null // Clear receiverId as chat is now established
                                }
                            }
                        }
                        setEffect { ChatContract.Effect.MessageSentSuccessfully }
                        setEffect { ChatContract.Effect.ScrollToBottom }
                    }
                    is Resource.Error -> {
                        Timber.e("SendMessage: Error - ${resource.message}")
                        setState { copy(sendMessageError = resource.message ?: "Failed to send message") }
                        // TODO: Handle reverting optimistic message or showing retry option for the specific message
                    }
                }
            }
            .launchIn(viewModelScope)
    }


    private var lastSelfTypingStateSent = false
    private fun sendTypingIndicator(chatId: String, isTyping: Boolean) {
        // ... (sendTypingIndicator implementation as before) ...
        // Be cautious if chatId is "new_chat_with_..." as backend might not recognize it for typing
        if (chatId.startsWith("new_chat_with_") && isTyping) { // Don't send typing for unestablished chat
            Timber.d("sendTypingIndicator: Suppressing typing indicator for unestablished chat.")
            return
        }
        if (isTyping == lastSelfTypingStateSent && isTyping) return
        if (!isTyping && !lastSelfTypingStateSent) return

        lastSelfTypingStateSent = isTyping
        selfTypingIndicatorJob?.cancel()
        selfTypingIndicatorJob = viewModelScope.launch {
            if (isTyping) {
                messageUseCases.sendTypingIndicator(chatId, true)
            } else {
                delay(1500)
                messageUseCases.sendTypingIndicator(chatId, false)
                lastSelfTypingStateSent = false
            }
        }
    }

    private fun markMessagesAsRead(chatId: String, messageIds: List<String>) {
        // ... (markMessagesAsRead implementation as before) ...
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            messageUseCases.updateMessageStatus(chatId, messageIds, Message.STATUS_READ)
            Timber.d("Attempted to mark messages as read: $messageIds")
        }
    }

    override fun onCleared() {
        super.onCleared()
        messagesJob?.cancel()
        olderMessagesJob?.cancel()
        typingIndicatorObservationJob?.cancel()
        selfTypingIndicatorJob?.cancel()
        clearOtherUserTypingJob?.cancel()

        uiState.value.chatId?.let { currentChatIdValue ->
            if (lastSelfTypingStateSent && !currentChatIdValue.startsWith("new_chat_with_")) {
                viewModelScope.launch { messageUseCases.sendTypingIndicator(currentChatIdValue, false) }
            }
        }
    }
}