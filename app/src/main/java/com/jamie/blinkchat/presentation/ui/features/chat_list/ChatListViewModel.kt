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
        // Observe auth token to react to logout from other places or token expiry
        observeAuthToken()
        // Initially load the chat list
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
                setEffect { ChatListContract.Effect.NavigateToChat(intent.chatId) }
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
            .distinctUntilChanged() // Only react if token presence actually changes
            .onEach { token ->
                if (token == null && uiState.value.isUserLoggedIn) {
                    // Token was cleared, likely logged out from elsewhere or expired
                    Timber.i("ChatListViewModel: Auth token cleared, navigating to login.")
                    setState { copy(isUserLoggedIn = false, chats = emptyList()) } // Clear chats
                    setEffect { ChatListContract.Effect.NavigateToLogin }
                } else if (token != null && !uiState.value.isUserLoggedIn) {
                    // User logged in, perhaps reload chats if state indicates not logged in
                    setState { copy(isUserLoggedIn = true) }
                    loadChats(forceRefresh = true) // Refresh chats on re-login
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadChats(forceRefresh: Boolean) {
        // Cancel any ongoing chat list loading job to avoid multiple concurrent fetches
        chatListJob?.cancel()

        chatListJob = chatUseCases.getChatList(forceRefresh = forceRefresh)
            .onEach { result ->
                when (result) {
                    is Resource.Loading -> {
                        if (forceRefresh) {
                            setState { copy(isRefreshing = true, errorMessage = null) }
                        } else {
                            // Only set global isLoading if not already refreshing and chats are empty
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
                        // Don't clear existing chats on refresh error, just show message
                        val currentChats = if (forceRefresh) uiState.value.chats else emptyList()
                        setState {
                            copy(
                                isLoading = false,
                                isRefreshing = false,
                                chats = currentChats, // Keep existing chats on refresh error
                                errorMessage = result.message ?: "Failed to load chats."
                            )
                        }
                        // Optionally send a Snackbar effect for errors too
                        // setEffect { ChatListContract.Effect.ShowErrorSnackbar(result.message ?: "Failed to load chats.") }
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
                        setState { copy(isLoading = true) } // Show loading during logout
                    }
                    is Resource.Success -> {
                        Timber.i("ChatListViewModel: Logout successful.")
                        // Token observer will also trigger navigation, but explicit navigation here is fine.
                        // State update via token observer might be slightly delayed.
                        setState { copy(isLoading = false, isUserLoggedIn = false, chats = emptyList()) }
                        setEffect { ChatListContract.Effect.NavigateToLogin }
                    }
                    is Resource.Error -> {
                        Timber.w("ChatListViewModel: Logout failed - ${result.message}")
                        setState { copy(isLoading = false) } // Stop loading
                        setEffect { ChatListContract.Effect.ShowErrorSnackbar(result.message ?: "Logout failed.") }
                    }
                }
            }
            .launchIn(viewModelScope)
    }
}