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
    private var typingIndicatorJob: Job? = null

    init {
        viewModelScope.launch {
            val userResource = authUseCases.getCurrentUser().first()
            if (userResource is Resource.Success) {
                setState { copy(currentUserId = userResource.data?.id) }
            } else {
                Timber.e("Could not fetch current user ID in ChatViewModel")
                setEffect { ChatContract.Effect.ShowErrorSnackbar("Error: User session invalid.") }
            }
        }

        webSocketManager.connectionState
            .onEach { connectionState -> // Renamed parameter for clarity
                val isConnected = connectionState is WebSocketConnectionState.Connected
                setState { copy(isConnected = isConnected) }
                if (isConnected && uiState.value.chatId != null) {
                    Timber.d("ChatViewModel: WebSocket reconnected for chatId: ${uiState.value.chatId}")
                }
            }
            .launchIn(viewModelScope)

        val chatIdFromNav = savedStateHandle.get<String>(Screen.Chat.ARG_CHAT_ID)
        val encodedUsernameFromNav = savedStateHandle.get<String>(Screen.Chat.ARG_OTHER_USERNAME)

        if (chatIdFromNav != null) {
            val otherUsername = encodedUsernameFromNav?.let { encoded ->
                try {
                    URLDecoder.decode(encoded, StandardCharsets.UTF_8.toString())
                } catch (e: Exception) {
                    Timber.e(e, "Failed to decode username: $encoded")
                    null // Fallback if decoding fails
                }
            } ?: "Chat Partner" // Fallback if username is null after trying to decode

            // Set username directly in state AND trigger LoadChatDetails
            setState { copy(chatId = chatIdFromNav, otherParticipantUsername = otherUsername) }
            setIntent(ChatContract.Intent.LoadChatDetails(chatIdFromNav))
        } else {
            Timber.e("ChatViewModel: ChatId not found in SavedStateHandle.")
            setState { copy(isLoadingMessages = false, loadMessagesError = "Chat information missing.") }
        }
    }

    override fun createInitialState(): ChatContract.State {
        return ChatContract.State()
    }

    override fun handleIntent(intent: ChatContract.Intent) {
        // Use uiState.value.chatId as the source of truth after initial load
        val currentChatIdFromState = uiState.value.chatId

        when (intent) {
            is ChatContract.Intent.LoadChatDetails -> {
                // chatId and otherParticipantUsername should already be set in state from init
                setState { copy(isLoadingMessages = true, messages = emptyList(), loadMessagesError = null) }
                observeMessages(intent.chatId) // Use chatId from intent for this initial load
                // TODO: Refine how/when messages are marked as delivered/read.
                // For now, let's assume MessagesDisplayed intent from UI will handle "read".
                // "Delivered" would be an automatic status update from server/client ACKs.
                // markMessagesAs(intent.chatId, Message.STATUS_DELIVERED) // Example call, might not be needed here
            }
            is ChatContract.Intent.LoadOlderMessages -> {
                currentChatIdFromState?.let { loadOlderMessages(it) }
            }
            is ChatContract.Intent.InputTextChanged -> {
                setState { copy(currentInputText = intent.text) }
                currentChatIdFromState?.let { sendTypingIndicator(it, intent.text.isNotBlank()) }
            }
            is ChatContract.Intent.SendMessageClicked -> {
                currentChatIdFromState?.let { sendMessage(it, uiState.value.currentInputText) }
            }
            is ChatContract.Intent.ClearSendMessageError -> {
                setState { copy(sendMessageError = null) }
            }
            is ChatContract.Intent.ClearLoadMessagesError -> {
                setState { copy(loadMessagesError = null) }
            }
            is ChatContract.Intent.TypingIndicatorChanged -> {
                currentChatIdFromState?.let { sendTypingIndicator(it, intent.isTyping) }
            }
            is ChatContract.Intent.MessagesDisplayed -> {
                currentChatIdFromState?.let { markMessagesAsRead(it, intent.messageIds) }
            }
            is ChatContract.Intent.RetryLoadMessages -> {
                currentChatIdFromState?.let {
                    setState { copy(isLoadingMessages = true, loadMessagesError = null) }
                    observeMessages(it)
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
                        canLoadMoreOlderMessages = messages.isNotEmpty() // Re-evaluate this logic based on pagination results
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadOlderMessages(chatId: String) {
        if (uiState.value.isLoadingOlderMessages || !uiState.value.canLoadMoreOlderMessages) return
        olderMessagesJob?.cancel()

        setState { copy(isLoadingOlderMessages = true, loadMessagesError = null) }

        val offset = uiState.value.messages.size

        olderMessagesJob = viewModelScope.launch {
            when (val result = messageUseCases.loadOlderMessages(chatId, offset.toLong(), 20)) {
                is Resource.Success -> {
                    val fetchedCount = result.data ?: 0
                    setState {
                        copy(
                            isLoadingOlderMessages = false,
                            canLoadMoreOlderMessages = fetchedCount >= 20
                        )
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
                is Resource.Loading -> { /* State already updated */ }
            }
        }
    }

    private fun sendMessage(chatId: String, content: String, receiverId: String? = null) {
        if (content.isBlank()) return
        val trimmedContent = content.trim()
        setState { copy(currentInputText = "") }

        messageUseCases.sendMessage(trimmedContent, chatId, receiverId)
            .onEach { resource ->
                when (resource) {
                    is Resource.Loading -> { /* Optimistic update handled by UI observing message list */ }
                    is Resource.Success -> {
                        Timber.d("SendMessage: Success for message ${resource.data?.id}")
                        setEffect { ChatContract.Effect.MessageSentSuccessfully }
                        setEffect { ChatContract.Effect.ScrollToBottom }
                    }
                    is Resource.Error -> {
                        Timber.e("SendMessage: Error - ${resource.message}")
                        setState { copy(sendMessageError = resource.message ?: "Failed to send message") }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private var lastTypingStateSent = false
    private fun sendTypingIndicator(chatId: String, isTyping: Boolean) {
        if (isTyping == lastTypingStateSent && isTyping) return // Avoid resending if already typing
        if (!isTyping && !lastTypingStateSent) return // Avoid resending if already not typing

        lastTypingStateSent = isTyping
        typingIndicatorJob?.cancel()
        typingIndicatorJob = viewModelScope.launch {
            if (isTyping) {
                messageUseCases.sendTypingIndicator(chatId, true)
            } else {
                delay(1500) // Debounce "stopped typing"
                messageUseCases.sendTypingIndicator(chatId, false)
            }
        }
    }

    private fun markMessagesAsRead(chatId: String, messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            messageUseCases.updateMessageStatus(chatId, messageIds, Message.STATUS_READ)
            Timber.d("Attempted to mark messages as read: $messageIds")
        }
    }

    // `markMessagesAs` was a placeholder, specific logic is in `markMessagesAsRead`
    // private fun markMessagesAs(chatId: String, status: String) { ... }


    override fun onCleared() {
        super.onCleared()
        messagesJob?.cancel()
        olderMessagesJob?.cancel()
        typingIndicatorJob?.cancel()
        uiState.value.chatId?.let { currentChatIdValue ->
            if (lastTypingStateSent) {
                viewModelScope.launch { messageUseCases.sendTypingIndicator(currentChatIdValue, false) }
            }
        }
    }
}