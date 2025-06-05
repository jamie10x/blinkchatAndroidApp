package com.jamie.blinkchat.presentation.ui.features.chat_list

import androidx.lifecycle.viewModelScope
import com.jamie.blinkchat.core.common.Resource
import com.jamie.blinkchat.core.mvi.BaseViewModel
import com.jamie.blinkchat.domain.usecase.auth.AuthUseCases
import com.jamie.blinkchat.domain.usecase.chat_list.ChatUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatUseCases: ChatUseCases,
    private val authUseCases: AuthUseCases
) : BaseViewModel<ChatListContract.State, ChatListContract.Intent, ChatListContract.Effect>() {

    private var chatListJob: Job? = null

    init {
        observeAuthToken()
        setIntent(ChatListContract.Intent.LoadChatList)
    }

    override fun createInitialState(): ChatListContract.State {
        return ChatListContract.State()
    }

    override fun handleIntent(intent: ChatListContract.Intent) {
        when (intent) {
            is ChatListContract.Intent.LoadChatList -> {
                loadChats(forceRefresh = false)
            }
            is ChatListContract.Intent.RefreshChatList -> {
                loadChats(forceRefresh = true)
            }
            is ChatListContract.Intent.ChatClicked -> {
                // Find the chat item from the current state to get the username
                val chatItem = uiState.value.chats.find { it.chatId == intent.chatId }
                chatItem?.let {
                    // Emit effect with otherUsername
                    setEffect { ChatListContract.Effect.NavigateToChat(intent.chatId, it.otherParticipantUsername) }
                } ?: Timber.e("Chat item not found for ID: ${intent.chatId} when trying to navigate.")
            }
            is ChatListContract.Intent.LogoutClicked -> {
                logoutUser()
            }
            is ChatListContract.Intent.ErrorMessageShown -> {
                setState { copy(errorMessage = null) }
            }
        }
    }

    private fun observeAuthToken() {
        authUseCases.observeAuthToken()
            .distinctUntilChanged()
            .onEach { token ->
                if (token == null && uiState.value.isUserLoggedIn) {
                    Timber.i("ChatListViewModel: Auth token cleared, navigating to login.")
                    setState { copy(isUserLoggedIn = false, chats = emptyList()) }
                    setEffect { ChatListContract.Effect.NavigateToLogin }
                } else if (token != null && !uiState.value.isUserLoggedIn) {
                    setState { copy(isUserLoggedIn = true) }
                    loadChats(forceRefresh = true)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadChats(forceRefresh: Boolean) {
        chatListJob?.cancel()
        chatListJob = chatUseCases.getChatList(forceRefresh = forceRefresh)
            .onEach { result ->
                when (result) {
                    is Resource.Loading -> {
                        if (forceRefresh) {
                            setState { copy(isRefreshing = true, errorMessage = null) }
                        } else {
                            if (uiState.value.chats.isEmpty()) {
                                setState { copy(isLoading = true, errorMessage = null) }
                            }
                        }
                    }
                    is Resource.Success -> {
                        Timber.d("ChatList: Loaded ${result.data?.size ?: 0} chats.")
                        setState {
                            copy(
                                isLoading = false,
                                isRefreshing = false,
                                chats = result.data ?: emptyList(),
                                errorMessage = null
                            )
                        }
                    }
                    is Resource.Error -> {
                        Timber.w("ChatList: Error loading chats - ${result.message}")
                        val currentChats = if (forceRefresh) uiState.value.chats else emptyList()
                        setState {
                            copy(
                                isLoading = false,
                                isRefreshing = false,
                                chats = currentChats,
                                errorMessage = result.message ?: "Failed to load chats."
                            )
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun logoutUser() {
        authUseCases.logoutUser()
            .onEach { result ->
                when (result) {
                    is Resource.Loading -> {
                        setState { copy(isLoading = true) }
                    }
                    is Resource.Success -> {
                        Timber.i("ChatListViewModel: Logout successful.")
                        setState { copy(isLoading = false, isUserLoggedIn = false, chats = emptyList()) }
                        setEffect { ChatListContract.Effect.NavigateToLogin }
                    }
                    is Resource.Error -> {
                        Timber.w("ChatListViewModel: Logout failed - ${result.message}")
                        setState { copy(isLoading = false) }
                        setEffect { ChatListContract.Effect.ShowErrorSnackbar(result.message ?: "Logout failed.") }
                    }
                }
            }
            .launchIn(viewModelScope)
    }
}